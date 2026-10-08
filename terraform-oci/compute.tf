data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_ocid
}

data "oci_core_images" "ubuntu_arm" {
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "22.04"
  shape                    = "VM.Standard.A1.Flex"
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

resource "oci_core_instance" "otp" {
  compartment_id      = var.compartment_ocid
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[0].name
  display_name        = "otp-server-ui"
  shape               = "VM.Standard.A1.Flex"

  shape_config {
    ocpus         = var.instance_ocpus
    memory_in_gbs = var.instance_memory_gbs
  }

  create_vnic_details {
    subnet_id        = oci_core_subnet.otp.id
    assign_public_ip = true
  }

  source_details {
    source_type = "image"
    source_id   = data.oci_core_images.ubuntu_arm.images[0].id
  }

  metadata = {
    ssh_authorized_keys = file(pathexpand(var.ssh_public_key_path))
    # templatefile() cannot call itself recursively (confirmed directly) --
    # both sub-templates are rendered here, at the top level, and the
    # already-rendered strings are what cloud-init.yaml.tftpl itself
    # receives, not nested templatefile() calls.
    user_data = base64encode(templatefile("${path.module}/cloud-init.yaml.tftpl", {
      caddyfile_content = templatefile("${path.module}/caddy/Caddyfile.tftpl", {
        otp_auth_token = var.otp_auth_token
      })
      refresh_script_content = templatefile("${path.module}/scripts/otp-graph-refresh.sh.tftpl", {
        github_owner            = var.github_owner
        graph_builder_image_tag = var.graph_builder_image_tag
      })
    }))
  }
}

# --- One-time deploy: env-file upload, initial graph upload, start the serving container ---
#
# The *initial* graph.obj is SCP'd from the already-built local copy
# (.tools/graph-builder/output/denmark-graph.obj, confirmed real and
# working throughout this project's history) via Terraform's own `file`
# provisioner, NOT produced by running the graph-builder container
# synchronously during apply. This deliberately decouples "does the
# VM/Caddy/backend work" from "does the graph-builder container work
# end-to-end" -- the graph-builder image is still fully wired into the
# weekly cron job (cloud-init.yaml.tftpl, above), and its first real
# execution is simply the first scheduled Monday run, not a synchronous,
# apply-blocking step. `local_graph_path`'s own default points at that
# same local file.
resource "null_resource" "deploy_otp" {
  depends_on = [oci_core_instance.otp]

  triggers = {
    otp_image_tag = var.otp_image_tag
    instance_id   = oci_core_instance.otp.id
    graph_sha     = filesha256(var.local_graph_path)
  }

  connection {
    type        = "ssh"
    host        = oci_core_instance.otp.public_ip
    user        = "ubuntu"
    private_key = file(pathexpand(var.ssh_private_key_path))
    timeout     = "5m"
  }

  # Terraform runs this resource's provisioners in the order they're
  # written. Without this first remote-exec, the two `file` provisioners
  # below ran *before* cloud-init's own `mkdir -p /home/ubuntu/otp-graph`
  # step -- confirmed directly against a real deploy: the graph upload's
  # destination directory didn't exist yet, so the SSH file-copy collapsed
  # it into a flat file literally named `otp-graph` (not a directory
  # containing graph.obj), and cloud-init's own later `mkdir -p` then
  # failed with "File exists". Waiting for cloud-init first guarantees the
  # directory is real before anything is uploaded into it.
  provisioner "remote-exec" {
    inline = [
      "cloud-init status --wait",
    ]
  }

  # GOOGLE_PLACES_API_KEY, written to an uploaded file -- never a `-e`
  # flag on a remote-exec command line (see the spec's own "Secrets"
  # section).
  provisioner "file" {
    content     = "GOOGLE_PLACES_API_KEY=${var.google_places_api_key}\n"
    destination = "/home/ubuntu/otp-server-ui.env"
  }

  # Initial graph, SCP'd directly -- see this resource's own comment above.
  provisioner "file" {
    source      = var.local_graph_path
    destination = "/home/ubuntu/otp-graph/graph.obj"
  }

  provisioner "remote-exec" {
    inline = [
      "chmod 600 /home/ubuntu/otp-server-ui.env",

      "sudo docker rm -f otp-server-ui || true",
      "sudo docker pull ghcr.io/${var.github_owner}/otp-server-ui:${var.otp_image_tag}",
      "sudo docker run -d --name otp-server-ui --restart unless-stopped -p 127.0.0.1:8081:8080 -v /home/ubuntu/otp-graph:/graph:ro -e GRAPH_FILE_PATH=/graph/graph.obj --env-file /home/ubuntu/otp-server-ui.env ghcr.io/${var.github_owner}/otp-server-ui:${var.otp_image_tag}",
    ]
  }
}
