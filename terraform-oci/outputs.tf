output "public_ip" {
  value = oci_core_instance.otp.public_ip
}

output "endpoint" {
  value = "https://otp.brj.one"
}
