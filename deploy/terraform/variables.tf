variable "cloud_id" {
  description = "Идентификатор облака Yandex Cloud (yc config get cloud-id)"
  type        = string
}

variable "folder_id" {
  description = "Идентификатор каталога (yc config get folder-id)"
  type        = string
}

variable "zone" {
  description = "Зона доступности"
  type        = string
  default     = "ru-central1-a"
}

variable "vm_user" {
  description = "Имя пользователя на виртуальной машине"
  type        = string
  default     = "deploy"
}

variable "ssh_public_key_path" {
  description = "Путь к публичному SSH-ключу"
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}


variable "repo_url" {
  description = "URL публичного git-репозитория с проектом. Если задан, ВМ сама клонирует и запускает проект"
  type        = string
  default     = ""
}

variable "core_fraction" {
  description = "Гарантированная доля vCPU, % (20, 50 или 100)"
  type        = number
  default     = 50
}

variable "preemptible" {
  description = "Прерываемая ВМ: дешевле, но останавливается не реже раза в сутки"
  type        = bool
  default     = false
}
