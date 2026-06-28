# Deployment guide

End-to-end procedure for deploying Hisab ERP to a fresh Hetzner Cloud VPS using the Terraform IaC and Docker Compose stack in this repo.

## Prerequisites

| | Tool / Account | Notes |
|---|---|---|
| Local | Terraform ≥ 1.6 | `brew install terraform` |
| Local | OpenTofu (optional) | drop-in for Terraform |
| Local | OpenSSL | for generating secrets |
| Local | Docker (or just rsync + ssh) | only needed if you want to test the prod compose locally |
| Account | Hetzner Cloud | API token (read+write) |
| Account | Domain registrar OR Cloudflare | DNS A records for `app.<domain>`, `pos.<domain>`, `api.<domain>`, `files.<domain>`, `grafana.<domain>` |
| Account | GitHub | for GHCR images |

## 0. Build & publish images

Push to `main` triggers `.github/workflows/backend.yml` and `frontend.yml`, which publish three images to GHCR:

```
ghcr.io/<owner>/hisaberp-backend:<short-sha>
ghcr.io/<owner>/hisaberp-admin:<short-sha>
ghcr.io/<owner>/hisaberp-pos:<short-sha>
```

For the **first manual deploy**, locally:

```bash
cd backend && docker build -t ghcr.io/<owner>/hisaberp-backend:bootstrap .
cd frontend && docker build -t ghcr.io/<owner>/hisaberp-admin:bootstrap -f apps/erp-admin/Dockerfile .
                docker build -t ghcr.io/<owner>/hisaberp-pos:bootstrap   -f apps/pos-terminal/Dockerfile .
docker push ghcr.io/<owner>/hisaberp-backend:bootstrap
docker push ghcr.io/<owner>/hisaberp-admin:bootstrap
docker push ghcr.io/<owner>/hisaberp-pos:bootstrap
```

Make the GHCR repo public OR create a GHCR pull token and `docker login` on the prod host.

## 1. Provision the server

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
$EDITOR terraform.tfvars                # set hcloud_token, root_domain, allowed_admin_cidrs

export HCLOUD_TOKEN="$(grep hcloud_token terraform.tfvars | cut -d'"' -f2)"

# Upload your SSH public key to Hetzner first via the console, label managed-by=hisaberp
# OR add it under terraform.tfvars `admin_ssh_keys = ["myname"]`.

terraform init
terraform plan
terraform apply
terraform output
```

Terraform creates: a private network, a firewall (only 22/80/443 from `allowed_admin_cidrs` for SSH), one `cx32` server, a 100 GB block volume mounted at `/var/lib/docker`, and (if Cloudflare token provided) the four DNS A records.

If you don't use Cloudflare, **manually** add A records pointing `app.<domain>`, `pos.<domain>`, `api.<domain>`, `files.<domain>` (and optionally `grafana.<domain>`) to the server's IPv4 address from `terraform output server_ipv4`. `files.<domain>` is REQUIRED — it serves stored files (product images, attachments).

## 2. Bootstrap secrets on the server

```bash
ssh deploy@<server-ip>

# Copy the env template and fill it in
sudo cp /etc/hisaberp/.env.template /etc/hisaberp/.env
sudo $EDITOR /etc/hisaberp/.env

# Generate strong values:
openssl rand -base64 32   # POSTGRES_PASSWORD
openssl rand -base64 32   # REDIS_PASSWORD
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 24 | head -c 32   # PII_ENCRYPTION_KEY  (must be exactly 32 chars)
openssl rand -base64 32   # MINIO_ROOT_PASSWORD
openssl rand -base64 24   # GRAFANA_ADMIN_PASSWORD

sudo chmod 600 /etc/hisaberp/.env
sudo chown root:root /etc/hisaberp/.env
```

## 3. Copy the compose file & start

From your local machine:

```bash
rsync -avz infra/docker/prod/docker-compose.yml \
  deploy@<server-ip>:/opt/hisaberp/docker-compose.yml

# Optional: observability addon
rsync -avz infra/observability/ deploy@<server-ip>:/opt/hisaberp/observability/
```

On the server:

```bash
cd /opt/hisaberp
sudo docker compose --env-file /etc/hisaberp/.env pull
sudo docker compose --env-file /etc/hisaberp/.env up -d
sudo docker compose --env-file /etc/hisaberp/.env logs -f backend   # watch the boot
```

The first boot runs Liquibase migrations (about 30s) then starts. Traefik will request a Let's Encrypt cert for each subdomain — the first request to `https://app.<domain>` may take 30-60 s.

Verify:

```bash
curl -fsS https://api.<domain>/api/v1/health
# {"status":"UP","timestamp":"...","service":"hisab-erp"}
```

### Object storage & file URLs (`files.<domain>`)

Uploaded files live in MinIO, whose S3 read API is exposed via Traefik at `files.<domain>` (needs
the `files.` A record). The backend talks to MinIO via the `MINIO_*` env vars (do **not** use
`S3_*` — they are ignored and the app falls back to insecure localhost defaults).

Access is split by sensitivity (policy set by the `minio-init` one-shot):
- **Product images** (`product-images/` prefix) — anonymously downloadable and browser-cacheable;
  the catalog stores a direct `https://files.<domain>/<bucket>/<object>` URL.
- **Sensitive documents** (subscription justificatifs, expense attachments) — the bucket is
  **private**. The backend stores only the object **key** and returns a short-lived **presigned**
  URL generated on each read (`StoragePresigner`, TTL `STORAGE_PRESIGN_TTL_SECONDS`, default 1 h).
  No anonymous access; the bucket cannot be listed. Legacy rows holding a full URL are tolerated
  (the key is extracted and re-signed).

> Presigning signs against the **public** host (`files.<domain>`), so that A record + Traefik route
> are required even for the private objects. Keep Traefik's `Host` header pass-through (default) so
> MinIO validates the SigV4 signature against the same host.

## 4. Bootstrap the first super-admin

Production does NOT auto-seed (the dev seeder is `@Profile("dev")`). The backend ships an
**idempotent, env-driven bootstrap** (`ProdAdminBootstrap`): set two env vars for the FIRST deploy
and it creates the reserved platform organization + a platform super-admin at startup. It no-ops
once a super-admin exists (safe across redeploys). Recommended flow:

1. Before the first `compose up`, add to `/etc/hisaberp/.env`:

   ```
   BOOTSTRAP_ADMIN_EMAIL=you@yourcompany.com
   BOOTSTRAP_ADMIN_PASSWORD=<a strong secret, ≥ 12 chars>
   ```

2. Start the stack (section 3) and watch the backend log for:
   `Prod admin bootstrap: created platform super-admin '...'`.

3. Sign in at `https://app.<domain>` via the **platform login**
   (`POST /api/v1/auth/platform-login`, no tenant code).

4. **Blank** `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` in `.env`, rotate the password
   from the console, and redeploy.

Real business tenants are then created from the super-admin console (which starts them in TRIAL).

> The password is hashed with the app's Argon2id encoder — do **not** try to `INSERT` a user with
> a plaintext or pre-hashed password directly in SQL.

## 5. Observability (optional)

```bash
cd /opt/hisaberp
sudo docker compose \
  -f docker-compose.yml \
  -f observability/docker-compose.observability.yml \
  --env-file /etc/hisaberp/.env up -d

# Grafana: https://grafana.<domain>  (admin / $GRAFANA_ADMIN_PASSWORD)
```

## 6. Backups

**Daily `pg_dump`** (the `pgbackup` sidecar): stored in the `pgbackup-data` volume, retention
14 days daily / 8 weeks weekly / 12 months monthly.

**Off-host copy — enable this.** The `backup-offsite` sidecar (rclone) replicates those dumps to
S3-compatible object storage. Without it, backups live only on the same VM and are lost with it.
Create a bucket + S3 keys in the Hetzner console, then set in `/etc/hisaberp/.env`:

```
BACKUP_S3_ENDPOINT=https://<region>.your-objectstorage.com
BACKUP_S3_BUCKET=hisaberp-backups
BACKUP_S3_ACCESS_KEY=...
BACKUP_S3_SECRET_KEY=...
```

The sidecar no-ops (exits 0) until these are set. **Also back up MinIO object data off-host**
(e.g. `mc mirror` to the same bucket) — the pg dumps do **not** include uploaded files.

**RPO ≈ 1 min** needs continuous **WAL archiving**, not just daily dumps (which give RPO up to 24h).
Use pgBackRest or wal-g shipping WAL to the same object storage. (Minimal manual variant — set
`archive_mode=on`, `wal_level=replica`, an `archive_command` writing to a mounted dir, plus a
1-minute uploader sidecar — also works.)

**Always rehearse a restore** (section 8) — an untested backup is not a backup.

## 7. Rolling upgrade

Once you have new image tags built by CI:

```bash
# from your local machine, with GitHub CLI auth
gh workflow run deploy-prod.yml -f version=v0.2.0
# OR manually:
ssh deploy@<server-ip>
sudo sed -i 's/^APP_VERSION=.*/APP_VERSION=v0.2.0/' /etc/hisaberp/.env
cd /opt/hisaberp && sudo docker compose --env-file /etc/hisaberp/.env pull && \
                   sudo docker compose --env-file /etc/hisaberp/.env up -d
```

Liquibase runs at app start; if a migration fails, the container exits and Compose keeps the previous version running (because we use `depends_on: condition: service_healthy` on Postgres only — failed backend will not bring down dependents).

## 8. Disaster recovery (RTO 4h target)

Worst case — server lost:

1. `terraform apply` again (gives new IP).
2. Update DNS A records (5-min TTL → instant if Cloudflare is your DNS).
3. Restore Postgres from the latest off-host backup.
4. Restore MinIO `/var/lib/docker/volumes/hisaberp-prod_minio-data` from off-host.
5. Run compose up.

Practice this every month — schedule a drill in Grafana alerts.

## 9. Hardening checklist

- [ ] Set `allowed_admin_cidrs` in `terraform.tfvars` to your office VPN (empty = SSH closed)
- [ ] Configure `BACKUP_S3_*` for off-host backups; add WAL archiving for RPO ≈ 1 min
- [ ] Unset `BOOTSTRAP_ADMIN_*` after the first deploy and rotate the super-admin password
- [ ] Sensitive files already use presigned URLs; tune `STORAGE_PRESIGN_TTL_SECONDS`, and presign product images too if your catalog must be private
- [ ] Rotate JWT_SECRET quarterly (forces all users to re-login)
- [ ] Enable MFA on the Hetzner account + SSH key only (no password auth — already in the cloud-init)
- [ ] Review `audit_log` weekly via the `/api/v1/audit` endpoint
- [ ] Set up alerts in Grafana for: 5xx rate > 1%, DB pool exhausted, backend memory > 80%
- [ ] Schedule monthly DR drill (restore backup to a throwaway env, verify smoke test passes)

## 10. Scale-out prerequisites (before running >1 backend replica)

The single-node compose stack is **not** safe to scale horizontally as-is. Fix two things first:

1. **Liquibase migrations** run at every backend boot. With multiple replicas this races
   (concurrent DDL on one schema). Move migrations to a dedicated **pre-deploy job**: set
   `spring.liquibase.enabled=false` on the app replicas and run Liquibase once, as the owner role,
   before rolling the new image.

2. **`@Scheduled` jobs** (tenant expiry, recurring expenses, overdue-invoice detection, lot
   expiry) have **no leader election** — every replica would run them, double-firing side effects
   and emails. Add **ShedLock** (a DB-backed lock) around the scheduled methods so exactly one
   replica runs each.

Also keep every replica's datasource on the non-superuser `hisaberp_app` role (RLS depends on it),
distribute the same `JWT_SECRET` to all replicas, and consider moving Postgres to managed Hetzner
PG with PITR at this point.
