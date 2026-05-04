variable "hcloud_token" {
  description = "Hetzner Cloud API token (project-scoped, RW)."
  type        = string
  sensitive   = true
}

variable "cloudflare_api_token" {
  description = "Cloudflare API token with Zone:Edit on the target zone (optional — set null to skip DNS)."
  type        = string
  sensitive   = true
  default     = null
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone id for ROOT_DOMAIN (only used if cloudflare_api_token is set)."
  type        = string
  default     = null
}

variable "root_domain" {
  description = "Apex domain (e.g. minierp.example.com)."
  type        = string
}

variable "admin_ssh_keys" {
  description = "List of public SSH key fingerprints already uploaded to Hetzner."
  type        = list(string)
}

variable "location" {
  description = "Hetzner location (fsn1=Falkenstein DE, hel1=Helsinki, nbg1=Nuremberg, ash=Ashburn, hil=Hillsboro)."
  type        = string
  default     = "fsn1"
}

variable "server_type" {
  description = "Hetzner server type. Recommended baseline: cx32 (4 vCPU, 8 GB)."
  type        = string
  default     = "cx32"
}

variable "image" {
  description = "Hetzner OS image. Recommended: Debian 12."
  type        = string
  default     = "debian-12"
}

variable "extra_volume_gb" {
  description = "Extra block volume for /var/lib/docker (PG data, MinIO, backups)."
  type        = number
  default     = 100
}

variable "allowed_admin_cidrs" {
  description = "CIDRs allowed to SSH (port 22). Defaults to no one — set this!"
  type        = list(string)
  default     = []
}
