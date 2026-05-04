# Deployment guide

End-to-end procedure for deploying Mini-ERP to a fresh Hetzner Cloud VPS using the Terraform IaC and Docker Compose stack in this repo.

## Prerequisites

| | Tool / Account | Notes |
|---|---|---|
| Local | Terraform ≥ 1.6 | `brew install terraform` |
| Local | OpenTofu (optional) | drop-in for Terraform |
| Local | OpenSSL | for generating secrets |
| Local | Docker (or just rsync + ssh) | only needed if you want to test the prod compose locally |
| Account | Hetzner Cloud | API token (read+write) |
| Account | Domain registrar OR Cloudflare | DNS A records for `app.<domain>`, `pos.<domain>`, `api.<domain>`, `grafana.<domain>` |
| Account | GitHub | for GHCR images |

## 0. Build & publish images

Push to `main` triggers `.github/workflows/backend.yml` and `frontend.yml`, which publish three images to GHCR:

```
ghcr.io/<owner>/minierp-backend:<short-sha>
ghcr.io/<owner>/minierp-admin:<short-sha>
ghcr.io/<owner>/minierp-pos:<short-sha>
```

For the **first manual deploy**, locally:

```bash
cd backend && docker build -t ghcr.io/<owner>/minierp-backend:bootstrap .
cd frontend && docker build -t ghcr.io/<owner>/minierp-admin:bootstrap -f apps/erp-admin/Dockerfile .
                docker build -t ghcr.io/<owner>/minierp-pos:bootstrap   -f apps/pos-terminal/Dockerfile .
docker push ghcr.io/<owner>/minierp-backend:bootstrap
docker push ghcr.io/<owner>/minierp-admin:bootstrap
docker push ghcr.io/<owner>/minierp-pos:bootstrap
```

Make the GHCR repo public OR create a GHCR pull token and `docker login` on the prod host.

## 1. Provision the server

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
$EDITOR terraform.tfvars                # set hcloud_token, root_domain, allowed_admin_cidrs

export HCLOUD_TOKEN="$(grep hcloud_token terraform.tfvars | cut -d'"' -f2)"

# Upload your SSH public key to Hetzner first via the console, label managed-by=minierp
# OR add it under terraform.tfvars `admin_ssh_keys = ["myname"]`.

terraform init
terraform plan
terraform apply
terraform output
```

Terraform creates: a private network, a firewall (only 22/80/443 from `allowed_admin_cidrs` for SSH), one `cx32` server, a 100 GB block volume mounted at `/var/lib/docker`, and (if Cloudflare token provided) the four DNS A records.

If you don't use Cloudflare, **manually** add A records pointing `app.<domain>`, `pos.<domain>`, `api.<domain>` (and optionally `grafana.<domain>`) to the server's IPv4 address from `terraform output server_ipv4`.

## 2. Bootstrap secrets on the server

```bash
ssh deploy@<server-ip>

# Copy the env template and fill it in
sudo cp /etc/minierp/.env.template /etc/minierp/.env
sudo $EDITOR /etc/minierp/.env

# Generate strong values:
openssl rand -base64 32   # POSTGRES_PASSWORD
openssl rand -base64 32   # REDIS_PASSWORD
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 24 | head -c 32   # PII_ENCRYPTION_KEY  (must be exactly 32 chars)
openssl rand -base64 32   # MINIO_ROOT_PASSWORD
openssl rand -base64 24   # GRAFANA_ADMIN_PASSWORD

sudo chmod 600 /etc/minierp/.env
sudo chown root:root /etc/minierp/.env
```

## 3. Copy the compose file & start

From your local machine:

```bash
rsync -avz infra/docker/prod/docker-compose.yml \
  deploy@<server-ip>:/opt/minierp/docker-compose.yml

# Optional: observability addon
rsync -avz infra/observability/ deploy@<server-ip>:/opt/minierp/observability/
```

On the server:

```bash
cd /opt/minierp
sudo docker compose --env-file /etc/minierp/.env pull
sudo docker compose --env-file /etc/minierp/.env up -d
sudo docker compose --env-file /etc/minierp/.env logs -f backend   # watch the boot
```

The first boot runs Liquibase migrations (about 30s) then starts. Traefik will request a Let's Encrypt cert for each subdomain — the first request to `https://app.<domain>` may take 30-60 s.

Verify:

```bash
curl -fsS https://api.<domain>/api/v1/health
# {"status":"UP","timestamp":"...","service":"mini-erp"}
```

## 4. Bootstrap the first super-admin

Production does NOT auto-seed a tenant (the dev seeder only runs in the `dev` profile). Create the first organization + super-admin via:

```bash
# SSH to the server, then from inside the backend container:
sudo docker compose --env-file /etc/minierp/.env exec backend sh -c '
  apk add --no-cache postgresql-client
  PGPASSWORD=$SPRING_DATASOURCE_PASSWORD psql -h postgres -U minierp -d minierp <<SQL
    -- 1. Create the org (use the API instead in normal cases)
    INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status)
    VALUES (gen_random_uuid(), '\''platform'\'', '\''Platform'\'', '\''BOUTIQUE'\'',
            '\''MRU'\'', '\''fr'\'', '\''Africa/Nouakchott'\'', '\''ACTIVE'\'')
    RETURNING id;
SQL'
```

Then make a one-shot HTTP call against the new org to create the SUPER_ADMIN — easier path: temporarily set `SPRING_PROFILES_ACTIVE=prod,bootstrap`, expose a one-time `/api/v1/admin/bootstrap` endpoint (TODO in Phase 1).

> A clean **bootstrap CLI** is on the Phase 1 backlog. Until then this manual SQL is the path.

## 5. Observability (optional)

```bash
cd /opt/minierp
sudo docker compose \
  -f docker-compose.yml \
  -f observability/docker-compose.observability.yml \
  --env-file /etc/minierp/.env up -d

# Grafana: https://grafana.<domain>  (admin / $GRAFANA_ADMIN_PASSWORD)
```

## 6. Backups

Automated daily `pg_dump`:

- Stored in the `pgbackup-data` volume at `/var/lib/docker/volumes/minierp-prod_pgbackup-data/_data/last/`.
- 14 days daily / 8 weeks weekly / 12 months monthly retention.

**To meet the spec's RPO 1-min target you must enable WAL archiving** to off-host storage (Hetzner Object Storage or external S3). Add the following to the `postgres` service in `docker-compose.yml`:

```yaml
command:
  - "postgres"
  - "-c"
  - "archive_mode=on"
  - "-c"
  - "archive_command=test ! -f /backups/wal/%f && cp %p /backups/wal/%f"
  - "-c"
  - "wal_level=replica"
volumes:
  - /var/lib/minierp/wal:/backups/wal
```

And run a sidecar that uploads `/var/lib/minierp/wal/*` to S3 every minute (e.g. `restic backup` or `rclone sync`).

## 7. Rolling upgrade

Once you have new image tags built by CI:

```bash
# from your local machine, with GitHub CLI auth
gh workflow run deploy-prod.yml -f version=v0.2.0
# OR manually:
ssh deploy@<server-ip>
sudo sed -i 's/^APP_VERSION=.*/APP_VERSION=v0.2.0/' /etc/minierp/.env
cd /opt/minierp && sudo docker compose --env-file /etc/minierp/.env pull && \
                   sudo docker compose --env-file /etc/minierp/.env up -d
```

Liquibase runs at app start; if a migration fails, the container exits and Compose keeps the previous version running (because we use `depends_on: condition: service_healthy` on Postgres only — failed backend will not bring down dependents).

## 8. Disaster recovery (RTO 4h target)

Worst case — server lost:

1. `terraform apply` again (gives new IP).
2. Update DNS A records (5-min TTL → instant if Cloudflare is your DNS).
3. Restore Postgres from the latest off-host backup.
4. Restore MinIO `/var/lib/docker/volumes/minierp-prod_minio-data` from off-host.
5. Run compose up.

Practice this every month — schedule a drill in Grafana alerts.

## 9. Hardening checklist

- [ ] Set `allowed_admin_cidrs` in `terraform.tfvars` to your office VPN
- [ ] Enable WAL archiving + off-host backup (S3)
- [ ] Rotate JWT_SECRET quarterly (forces all users to re-login)
- [ ] Enable MFA on the Hetzner account + SSH key only (no password auth — already in the cloud-init)
- [ ] Review `audit_log` weekly via the `/api/v1/audit` endpoint
- [ ] Set up alerts in Grafana for: 5xx rate > 1%, DB pool exhausted, backend memory > 80%
- [ ] Schedule monthly DR drill (restore backup to a throwaway env, verify smoke test passes)
