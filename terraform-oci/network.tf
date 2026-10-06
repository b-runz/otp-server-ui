resource "oci_core_vcn" "otp" {
  compartment_id = var.compartment_ocid
  cidr_blocks    = ["10.0.0.0/16"]
  display_name   = "otp-server-ui-vcn"
  dns_label      = "otpserverui"
}

resource "oci_core_internet_gateway" "otp" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.otp.id
  display_name   = "otp-server-ui-igw"
  enabled        = true
}

resource "oci_core_route_table" "otp" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.otp.id
  display_name   = "otp-server-ui-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.otp.id
  }
}

# Only SSH (22, one-time deploy) and 80/443 (Caddy) are open. The serving
# backend only ever listens on loopback (127.0.0.1:8081).
resource "oci_core_security_list" "otp" {
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.otp.id
  display_name   = "otp-server-ui-seclist"

  egress_security_rules {
    protocol    = "all"
    destination = "0.0.0.0/0"
  }

  ingress_security_rules {
    protocol = "6"
    source   = "0.0.0.0/0"
    tcp_options {
      min = 22
      max = 22
    }
  }

  ingress_security_rules {
    protocol = "6"
    source   = "0.0.0.0/0"
    tcp_options {
      min = 80
      max = 80
    }
  }

  ingress_security_rules {
    protocol = "6"
    source   = "0.0.0.0/0"
    tcp_options {
      min = 443
      max = 443
    }
  }
}

resource "oci_core_subnet" "otp" {
  compartment_id             = var.compartment_ocid
  vcn_id                     = oci_core_vcn.otp.id
  cidr_block                 = "10.0.1.0/24"
  display_name               = "otp-server-ui-subnet"
  dns_label                  = "otpsubnet"
  route_table_id             = oci_core_route_table.otp.id
  security_list_ids          = [oci_core_security_list.otp.id]
  prohibit_public_ip_on_vnic = false
}
