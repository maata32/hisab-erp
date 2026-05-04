# DNS records — only created if a Cloudflare token is provided.

locals {
  dns_records = var.cloudflare_api_token != null ? [
    { name = "app.${var.root_domain}",     value = hcloud_server.app.ipv4_address, type = "A" },
    { name = "pos.${var.root_domain}",     value = hcloud_server.app.ipv4_address, type = "A" },
    { name = "api.${var.root_domain}",     value = hcloud_server.app.ipv4_address, type = "A" },
    { name = "grafana.${var.root_domain}", value = hcloud_server.app.ipv4_address, type = "A" },
  ] : []
}

resource "cloudflare_record" "app_records" {
  for_each = { for r in local.dns_records : r.name => r }

  zone_id = var.cloudflare_zone_id
  name    = each.value.name
  content = each.value.value
  type    = each.value.type
  ttl     = 300
  proxied = false
}
