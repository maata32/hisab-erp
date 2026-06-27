# Hisab ERP

Multi-tenant SaaS Hisab ERP for Mauritanian retail and wholesale (boutiques, supermarkets, wholesalers). Tri-lingual UI (Français / العربية / English with full RTL), offline-capable POS, multi-warehouse inventory with FEFO lot/expiry tracking, integrated payments and customer-credit module.

> **Status:** Phase 0 Foundation deployed. Phase 1A (POS MVP) — in progress. See [docs/ROADMAP.md](docs/ROADMAP.md).

## What's in here

| Area | Path | Notes |
|---|---|---|
| Backend | [`backend/`](backend/) | Java 21, Spring Boot 3.3, Spring Modulith multi-module Maven |
| Frontend | [`frontend/`](frontend/) | Nx 19 monorepo: `erp-admin` (Angular 18) + `pos-terminal` (PWA) |
| Infra | [`infra/`](infra/) | Docker Compose (dev + prod), Hetzner Terraform IaC, observability |
| Docs | [`docs/`](docs/) | Architecture, decisions, deployment guide |
| CI | [`.github/workflows/`](.github/workflows/) | Build, test, scan, deploy |

## Quick start (5 minutes)

Prerequisites: Docker 24+, Make, JDK 21 (only if you want to run backend locally), Node 20+ (only for the frontend dev server).

```bash
git clone <repo> hisab-erp && cd hisab-erp
make dev-up
```

Wait ~90s for the backend to migrate the schema and seed the demo tenant. Then open:

| | URL | Credentials |
|---|---|---|
| **Admin frontend** | http://localhost:4200 | tenant `demo`, `admin@demo.local` / `Admin1234!` |
| **POS frontend** | http://localhost:4201 | same |
| **Backend API** | http://localhost:8080/swagger-ui.html | JWT Bearer (login via `/api/v1/auth/login`) |
| **MailHog** | http://localhost:8025 | — |
| **MinIO** | http://localhost:9001 | `minioadmin` / `minioadmin` |
| **Postgres** | `psql -h localhost -U minierp -d hisaberp` | pwd `minierp` |

`make help` lists every command.

## Architecture in one paragraph

A **modular monolith** (Spring Modulith) with 4 bounded contexts in Phase 0 — `shared`, `tenant`, `identity`, `audit` — each in its own Maven module with explicit allowed dependencies enforced by ArchUnit + Modulith verifier. Multi-tenancy is **defense-in-depth**: every tenant-aware row has a `tenant_id` filtered both by a Hibernate `@Filter` and a PostgreSQL Row-Level Security policy keyed off `app.current_tenant`. JWT auth (15 min access + 7 day rotated refresh) carries the tenant id. The Angular front is an Nx monorepo with two standalone-component apps and four shared libs (`shared-api`, `shared-auth`, `shared-i18n`, `shared-ui`); the POS app is a PWA with offline scaffolding. Production runs as a single Docker Compose stack on Hetzner Cloud behind Traefik with Let's Encrypt — see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

Full deep-dive: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## What's implemented vs. planned

**Phase 0 (this build) — DONE:**
- Multi-tenant foundation (Hibernate filter + Postgres RLS, cross-tenant isolation tests)
- JWT auth with refresh tokens, account lockout, Argon2id password hashing
- 9 RBAC roles + 50+ permissions seeded per tenant
- Organization/Subscription/TenantSettings entities + REST API
- Append-only audit log (DB trigger blocks UPDATE/DELETE)
- i18n FR/AR/EN with RTL handling
- Liquibase migrations + dev seed (demo tenant + admin)
- Dev/prod Docker Compose stacks, Hetzner Terraform, observability addon
- CI: build, test, vulnerability scan, image push, manual deploy

**Phase 1A — TODO (subsequent sessions):**
- Catalog module (products, variants, brands, categories)
- UoM categories + pricing tiers + multi-UoM products
- Inventory (warehouses, stock movements, transfers, counts)
- POS counter (cart, payment, thermal receipt, cash sessions)
- Offline POS via Dexie.js + idempotent sync endpoint
- Lot/expiry FEFO + alerts
- Sales / invoicing / customers / deliveries
- Payments module with allocations and customer credits
- Notifications (SMS Chinguitel/Mauritel, email)

See [docs/ROADMAP.md](docs/ROADMAP.md) for the full plan.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Decision log (ADR)](docs/DECISIONS.md) — every spec ambiguity resolved with rationale
- [Deployment guide](docs/DEPLOYMENT.md)
- [Roadmap](docs/ROADMAP.md)
- [Contributing](docs/CONTRIBUTING.md)
- [Smoke test](docs/SMOKE_TEST.md) — verify a fresh dev install works

## License

Proprietary. © 2026 Hisab ERP team.
