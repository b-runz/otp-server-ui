variable "region" {
  description = "OCI region to deploy into, e.g. eu-stockholm-1."
  type        = string
}

variable "compartment_ocid" {
  description = "OCID of the compartment to create resources in."
  type        = string
}

variable "instance_ocpus" {
  type    = number
  default = 2
}

variable "instance_memory_gbs" {
  type    = number
  default = 12
}

variable "ssh_public_key_path" {
  type    = string
  default = "~/.ssh/id_ed25519.pub"
}

variable "ssh_private_key_path" {
  type    = string
  default = "~/.ssh/id_ed25519"
}

variable "github_owner" {
  description = "GitHub owner/org the GHCR images are published under."
  type        = string
}

variable "otp_image_tag" {
  type    = string
  default = "latest"
}

variable "graph_builder_image_tag" {
  type    = string
  default = "latest"
}

variable "local_graph_path" {
  description = "Path to the already-built local graph.obj, SCP'd as the initial graph (the weekly cron job takes over from there -- see null_resource.deploy_otp's own comment)."
  type        = string
  default     = "../.tools/graph-builder/output/denmark-graph.obj"
}

variable "otp_auth_token" {
  description = "Shared secret required in the X-Auth-Token header. Generate with: openssl rand -hex 32"
  type        = string
  sensitive   = true
}

variable "google_places_api_key" {
  description = "Set via TF_VAR_google_places_api_key -- never a .tfvars file."
  type        = string
  sensitive   = true
}
