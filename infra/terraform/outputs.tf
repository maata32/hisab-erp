output "server_ipv4" {
  description = "Public IPv4 address of the app server."
  value       = hcloud_server.app.ipv4_address
}

output "server_ipv6" {
  description = "Public IPv6 address."
  value       = hcloud_server.app.ipv6_address
}

output "ssh_command" {
  description = "Convenience SSH command."
  value       = "ssh deploy@${hcloud_server.app.ipv4_address}"
}

output "next_steps" {
  description = "Manual finalisation steps after terraform apply."
  value       = <<EOT
1. SSH in:                 ssh deploy@${hcloud_server.app.ipv4_address}
2. Fill the env file:      sudo cp /etc/minierp/.env.template /etc/minierp/.env && sudo vi /etc/minierp/.env
3. Copy the prod compose:  rsync -av infra/docker/prod/docker-compose.yml deploy@${hcloud_server.app.ipv4_address}:/opt/minierp/
4. Pull + start:           ssh deploy@${hcloud_server.app.ipv4_address} 'cd /opt/minierp && docker compose --env-file /etc/minierp/.env pull && docker compose --env-file /etc/minierp/.env up -d'
5. Verify TLS issuance:    https://app.${var.root_domain} (Let's Encrypt may take 30-60s)
EOT
}
