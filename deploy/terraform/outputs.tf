locals {
  ip = yandex_compute_instance.booking.network_interface[0].nat_ip_address
}

output "external_ip" {
  description = "Внешний IP-адрес виртуальной машины"
  value       = local.ip
}

output "app_url" {
  value = "http://${local.ip}/"
}

output "grafana_url" {
  value = "http://${local.ip}:3000/"
}

output "ssh" {
  value = "ssh ${var.vm_user}@${local.ip}"
}
