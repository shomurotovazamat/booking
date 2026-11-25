terraform {
  required_version = ">= 1.5"

  required_providers {
    yandex = {
      source  = "yandex-cloud/yandex"
      version = ">= 0.130"
    }
  }
}

# Токен берётся из переменной окружения YC_TOKEN (см. README)
provider "yandex" {
  cloud_id  = var.cloud_id
  folder_id = var.folder_id
  zone      = var.zone
}

# ---------- Сеть ----------

resource "yandex_vpc_network" "booking" {
  name = "booking-network"
}

resource "yandex_vpc_subnet" "booking" {
  name           = "booking-subnet"
  zone           = var.zone
  network_id     = yandex_vpc_network.booking.id
  v4_cidr_blocks = ["10.10.0.0/24"]
}

resource "yandex_vpc_security_group" "booking" {
  name       = "booking-sg"
  network_id = yandex_vpc_network.booking.id

  ingress {
    description    = "SSH"
    protocol       = "TCP"
    port           = 22
    v4_cidr_blocks = var.ssh_allowed_cidrs
  }

  ingress {
    description    = "HTTP: frontend + API"
    protocol       = "TCP"
    port           = 80
    v4_cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description    = "Grafana"
    protocol       = "TCP"
    port           = 3000
    v4_cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description    = "Исходящий трафик"
    protocol       = "ANY"
    v4_cidr_blocks = ["0.0.0.0/0"]
  }
}

# ---------- Виртуальная машина ----------

data "yandex_compute_image" "ubuntu" {
  family = "ubuntu-2204-lts"
}

resource "yandex_compute_instance" "booking" {
  name        = "booking-vm"
  hostname    = "booking-vm"
  platform_id = "standard-v3"
  zone        = var.zone

  resources {
    cores         = 2
    memory        = 4
    core_fraction = var.core_fraction
  }

  boot_disk {
    initialize_params {
      image_id = data.yandex_compute_image.ubuntu.id
      size     = 20
      type     = "network-ssd"
    }
  }

  network_interface {
    subnet_id          = yandex_vpc_subnet.booking.id
    nat                = true
    security_group_ids = [yandex_vpc_security_group.booking.id]
  }

  scheduling_policy {
    preemptible = var.preemptible
  }

  metadata = {
    user-data = templatefile("${path.module}/cloud-init.yaml", {
      vm_user        = var.vm_user
      ssh_public_key = trimspace(file(pathexpand(var.ssh_public_key_path)))
      repo_url       = var.repo_url
    })
  }
}
