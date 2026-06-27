# Architecture

This document covers the high-level design of Phase 0 and how Phase 1+ will plug in. For point decisions on spec ambiguities, see [DECISIONS.md](DECISIONS.md).

## 1. Repository layout

```
hisab-erp/
├── backend/                       # Spring Boot multi-module Maven
│   ├── shared/                    # Cross-cutting: TenantAware/AuditableEntity, errors, OpenAPI, i18n
│   ├── tenant/                    # Organization, SubscriptionPlan, TenantSettings
│   ├── identity/                  # User, Role, Permission, JWT, RBAC seed
│   ├── audit/                     # AuditLog (append-only)
│   └── bootstrap/                 # @SpringBootApplication, application.yml, migrations, dev seed
├── frontend/                      # Nx monorepo
│   ├── apps/
│   │   ├── erp-admin/             # Admin SPA (Angular 18)
│   │   └── pos-terminal/          # POS PWA (Angular 18 + Service Worker)
│   └── libs/
│       ├── shared-api/            # HTTP types, error model, interceptor
│       ├── shared-auth/           # AuthService, guards, JWT storage
│       ├── shared-i18n/           # ngx-translate config, LocaleService (RTL handling)
│       └── shared-ui/             # Reusable components (LoadingSpinner, EmptyState, LocaleSwitcher)
├── infra/
│   ├── docker/dev/                # docker-compose.yml for local dev
│   ├── docker/prod/               # docker-compose.yml + .env.example for VPS prod
│   ├── observability/             # Prometheus / Loki / Grafana / Tempo addon
│   └── terraform/                 # Hetzner Cloud + Cloudflare DNS
├── .github/workflows/             # backend / frontend / deploy-staging / deploy-prod
├── scripts/                       # postgres-init.sql etc.
└── docs/                          # ARCHITECTURE / DECISIONS / DEPLOYMENT / ROADMAP / SMOKE_TEST
```

## 2. Backend

### 2.1 Modular monolith

Spring Modulith enforces module boundaries: each `@ApplicationModule` declares its `allowedDependencies`, and `ModularityTests.verifies_module_boundaries()` fails the build if a module reaches into another's `internal` package. Modules talk via:

- **Public ports** in `<module>/api/` (interfaces, DTOs, records).
- **Domain events** in `<module>/events/` (records). Listeners in other modules use `@ApplicationModuleListener` which is async + transactional out-of-the-box.

Phase 0 dependency graph:

```
shared <— tenant <— identity
   ^         ^         ^
   └─── audit ─────────┘
                      bootstrap (wires everything)
```

When Phase 1A adds catalog/uom/pricing/inventory/pos/sales/payment/etc., they all depend on `shared` + `tenant` + `identity`, plus selected siblings via `api` ports only.

### 2.2 Multi-tenancy (defense-in-depth)

Every tenant-aware row carries `tenant_id UUID NOT NULL`. Two independent layers of enforcement:

1. **Hibernate `@Filter`** activated by `TenantSessionAspect` on every `@Transactional` method using the `tenantFilter` filter on `TenantAwareEntity`.
2. **PostgreSQL Row-Level Security** policy `tenant_isolation_*` USING `tenant_id = app_current_tenant()`. The session variable is set via `SELECT set_config('app.current_tenant', :v, true)` on each transaction.

`CrossTenantIsolationIT` proves both layers work — including a test where the Hibernate filter is intentionally **off** to verify RLS still blocks the query (ADR-017).

The HTTP entry-point (`JwtAuthenticationFilter`) reads the `tid` claim from the JWT and pushes it to `TenantContext` (ThreadLocal). All ThreadLocals (`TenantContext`, `CurrentUserHolder`, `RequestContext`) are cleared in the `finally` of the filter.

### 2.3 Auth

- Stateless **JWT (HS256)**: 15-min access token + 7-day refresh token (rotated on every refresh, hashed in DB).
- **Argon2id** password hashing (3 iterations, 64 MB).
- 5 failed attempts → 15-minute lockout (per-user, per-tenant).
- `@PreAuthorize` SpEL on controllers; tenant ownership checked via `tenantSecurity.isSelf(...)`.
- Public endpoints whitelist: `/api/v1/auth/login`, `/refresh`, `/forgot-password`, `/reset-password`, `/health`, `/actuator/health/**`, `/v3/api-docs`, `/swagger-ui`.

### 2.4 Audit

`AuditEvent` is published from any service via `ApplicationEventPublisher`. The audit module's `AuditEventListener` (`@ApplicationModuleListener`) persists it to `audit_log` async — and an exception there never breaks the publishing transaction. The table has a Postgres trigger `audit_log_no_update` / `audit_log_no_delete` that raises on UPDATE/DELETE — true append-only.

### 2.5 Error model

Single `GlobalExceptionHandler` translates all exceptions to `ApiError`:

```json
{
  "timestamp": "2026-05-04T12:34:56Z",
  "status": 422,
  "code": "validation.failed",
  "message": "Les données fournies ne sont pas valides.",
  "path": "/api/v1/users",
  "traceId": "abcdef...",
  "fieldErrors": [{"field": "email", "code": "Email", "message": "..."}]
}
```

Messages are i18n-resolved via `MessageSource` based on `Accept-Language` (FR / AR / EN; defaults to FR). Frontend re-resolves the `code` against its own translation bundles, so multilingual error display works even before the user logs in.

## 3. Frontend

### 3.1 Nx workspace

Two apps + four libs. Apps are `@nx/angular:application` standalone-component projects with strict TypeScript and PrimeNG + Tailwind. Libs are tagged `scope:shared` so the boundary rule in `.eslintrc.json` blocks circular references.

### 3.2 Auth flow

`AuthService` (`libs/shared-auth`) holds tokens in `sessionStorage` (cleared on close — by design for shared POS terminals). `apiInterceptor` (`libs/shared-api`):

1. Prefixes the request URL with the API base.
2. Adds `Authorization: Bearer <token>` and `Accept-Language`.
3. On `401`, calls `auth.refresh()` (deduplicated by `refreshInFlight$`), retries once, falls back to redirect-to-login if refresh fails.
4. On `403` redirects to `/forbidden`.

### 3.3 i18n + RTL

`LocaleService` switches `<html dir="...">` and `<html lang="...">` on locale change, and toggles a `body.rtl` class. Tailwind has no built-in RTL support; we rely on logical properties (`me-` / `ms-`) and the `[dir='rtl']` selector overrides for legacy properties.

### 3.4 POS PWA

The POS app registers `ngsw-worker.js` only in production. It pre-caches the app shell + assets and lazy-caches API reference data (e.g. `/api/v1/auth/me`, `/api/v1/health`). Phase 1A will add Dexie.js for offline sale storage and a sync queue.

`OnlineStatusService` combines `navigator.onLine` events with a 30-second backend heartbeat — matches the spec's connectivity strategy.

## 4. Infrastructure

### 4.1 Dev stack

`make dev-up` launches eight containers via `infra/docker/dev/docker-compose.yml`:

```
postgres   :5432         redis     :6379
minio      :9000/9001    mailhog   :1025/8025
backend    :8080
admin      :4200         pos       :4201
```

All wired together over the default Compose network. The backend runs in `dev` profile (verbose logs, full actuator, dev seeder enabled).

### 4.2 Prod stack

`infra/docker/prod/docker-compose.yml` is built for a single Hetzner CX32 (or larger) node:

- **Traefik 3** terminates TLS via Let's Encrypt (HTTP-01 challenge), routes `app.<domain>` → admin, `pos.<domain>` → pos, `api.<domain>` → backend, `grafana.<domain>` → grafana (when observability addon is enabled).
- All services on a private `hisaberp-internal` network; only Traefik on the public-facing one.
- `pgbackup` runs daily encrypted `pg_dump` to a local volume (extend with `aws s3 sync` to a Hetzner Object Storage bucket for off-host backups — RPO 1 minute target needs continuous WAL archiving, see [DEPLOYMENT.md](DEPLOYMENT.md)).
- All secrets in `/etc/hisaberp/.env` (chmod 600, root-owned); referenced via `${VAR}` in compose.

### 4.3 IaC

Terraform manages: Hetzner network + subnet + firewall, one `cx32` server, an attached 100 GB block volume mounted at `/var/lib/docker`, optional Cloudflare DNS records, and reverse-DNS PTR. Cloud-init bootstraps Docker, fail2ban, ufw (defense-in-depth on the host even though the cloud firewall already restricts ingress), and writes `/etc/hisaberp/.env.template`.

### 4.4 Observability

Optional addon stack: Prometheus scrapes Spring Boot `/actuator/prometheus` and Traefik `:8082`. Promtail tails Docker JSON logs into Loki. Tempo receives OTLP traces (Spring Boot Micrometer Tracing exports automatically). Grafana provisioned with all three datasources. Default panels are TBD in V1.

## 5. CI / CD

- **`backend.yml`** — Maven verify (unit + integration with Testcontainers), Modulith doc generation, OWASP Dependency-Check, Docker build & push to GHCR, Trivy scan.
- **`frontend.yml`** — Nx affected lint/test/build, Docker build & push for both apps, Trivy scan.
- **`deploy-staging.yml`** — Auto-runs on push to `main` if `STAGING_HOST` is set; rsyncs compose, pulls images, restarts, smoke tests.
- **`deploy-prod.yml`** — Manual `workflow_dispatch` only. Takes a `version` input, updates `APP_VERSION` in `/etc/hisaberp/.env`, restarts.
- **Dependabot** weekly for Maven + npm, monthly for Docker + Actions.
- **Branch convention**: `main` (prod), `develop` (staging), `feature/*` (PR target = `develop`). Conventional Commits.

## 6. Testing strategy

- **Unit**: JUnit 5 + Mockito + AssertJ. Target ≥ 80 % on services.
- **Slice**: `@DataJpaTest` for repos.
- **Integration**: `@SpringBootTest` + Testcontainers Postgres. Notable IT: `CrossTenantIsolationIT` proves RLS blocks even with the Hibernate filter off.
- **Architecture**: ArchUnit + Spring Modulith verifier (in `ModularityTests`).
- **Frontend unit**: Jest via `@nx/jest`.
- **Frontend e2e**: Playwright (TODO: write specs in Phase 1A).
- **Mandatory test cases** from §15.6 of the spec are tracked in [docs/ROADMAP.md](ROADMAP.md) and added module by module.

## 7. Observability of business events

Audit log is the source of truth for sensitive actions. Domain events (`OrganizationCreatedEvent`, etc.) are captured by Spring Modulith Events for retry/auditing and stored in the `event_publication` table by `spring-modulith-starter-jpa`.

Logs are JSON in production (`logback-spring.xml`'s `<springProfile name="prod">`) with `traceId`, `spanId`, `tenantId`, `userId` MDC fields — so Grafana Loki queries can correlate.
