terraform {
  required_version = ">= 1.6.0"
  required_providers {
    hcloud = {
      source  = "hetznercloud/hcloud"
      version = "~> 1.48"
    }
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 4.43"
    }
  }
  backend "s3" {
    # Use Hetzner Object Storage or any S3-compatible bucket for state.
    # Configure via -backend-config at init time:
    #   terraform init \
    #     -backend-config="bucket=minierp-tfstate" \
    #     -backend-config="key=prod/terraform.tfstate" \
    #     -backend-config="region=fsn1" \
    #     -backend-config="endpoint=https://fsn1.your-objectstorage.com"
    skip_credentials_validation = true
    skip_region_validation      = true
    skip_metadata_api_check     = true
    use_path_style              = true
  }
}

provider "hcloud" {
  token = var.hcloud_token
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}
