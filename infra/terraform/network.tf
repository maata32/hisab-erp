resource "hcloud_network" "main" {
  name     = "hisaberp-${var.location}"
  ip_range = "10.10.0.0/16"
  labels = {
    project = "hisaberp"
    env     = "prod"
  }
}

resource "hcloud_network_subnet" "app" {
  network_id   = hcloud_network.main.id
  type         = "cloud"
  network_zone = hcloud_network.main.id != "" ? local.network_zone : local.network_zone
  ip_range     = "10.10.1.0/24"
}

locals {
  network_zone_map = {
    fsn1 = "eu-central"
    nbg1 = "eu-central"
    hel1 = "eu-central"
    ash  = "us-east"
    hil  = "us-west"
  }
  network_zone = local.network_zone_map[var.location]
}

resource "hcloud_firewall" "app" {
  name = "hisaberp-${var.location}-app"

  # SSH only from allowed CIDRs
  dynamic "rule" {
    for_each = length(var.allowed_admin_cidrs) > 0 ? [1] : []
    content {
      direction  = "in"
      protocol   = "tcp"
      port       = "22"
      source_ips = var.allowed_admin_cidrs
    }
  }

  rule {
    direction  = "in"
    protocol   = "tcp"
    port       = "80"
    source_ips = ["0.0.0.0/0", "::/0"]
  }

  rule {
    direction  = "in"
    protocol   = "tcp"
    port       = "443"
    source_ips = ["0.0.0.0/0", "::/0"]
  }

  # ICMP for health probes
  rule {
    direction  = "in"
    protocol   = "icmp"
    source_ips = ["0.0.0.0/0", "::/0"]
  }

  labels = { project = "hisaberp" }
}
