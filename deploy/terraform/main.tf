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

# Отдельная группа безопасности не создаётся: API Yandex Cloud отклонил
# добавление входящих правил (PermissionDenied). ВМ получает группу
# безопасности сети по умолчанию; наружу Docker публикует только порты 80 и 3000,
# PostgreSQL и backend доступны только внутри сети Docker, Prometheus — только
# на 127.0.0.1.

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
      type     = "network-hdd"
    }
  }

  network_interface {
    subnet_id = yandex_vpc_subnet.booking.id
    nat       = true
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
