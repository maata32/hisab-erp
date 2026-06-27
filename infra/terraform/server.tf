data "hcloud_ssh_keys" "admins" {
  with_selector = "managed-by=hisaberp"
}

resource "hcloud_volume" "data" {
  name      = "hisaberp-${var.location}-data"
  size      = var.extra_volume_gb
  location  = var.location
  format    = "ext4"
  automount = false
  labels = { project = "hisaberp" }
}

resource "hcloud_server" "app" {
  name         = "hisaberp-app-${var.location}"
  image        = var.image
  server_type  = var.server_type
  location     = var.location
  ssh_keys     = var.admin_ssh_keys
  firewall_ids = [hcloud_firewall.app.id]

  network {
    network_id = hcloud_network.main.id
    ip         = "10.10.1.10"
  }

  user_data = templatefile("${path.module}/cloud-init.yml", {
    root_domain        = var.root_domain
    docker_volume_path = "/dev/disk/by-id/scsi-0HC_Volume_${hcloud_volume.data.id}"
  })

  labels = {
    project = "hisaberp"
    env     = "prod"
    role    = "app"
  }

  depends_on = [hcloud_network_subnet.app]
}

resource "hcloud_volume_attachment" "data" {
  volume_id = hcloud_volume.data.id
  server_id = hcloud_server.app.id
  automount = false
}

resource "hcloud_rdns" "app_v4" {
  server_id  = hcloud_server.app.id
  ip_address = hcloud_server.app.ipv4_address
  dns_ptr    = "app.${var.root_domain}"
}
