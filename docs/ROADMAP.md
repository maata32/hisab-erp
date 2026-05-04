# Roadmap

Snapshot as of 2026-05-04. Updated each sprint.

## Phase 0 — Foundation ✅ DELIVERED

| | Item | Status |
|---|---|---|
| ✅ | Monorepo (backend + frontend + infra) | Done |
| ✅ | Spring Modulith multi-module backend | Done |
| ✅ | Multi-tenant infra: Hibernate filter + Postgres RLS | Done |
| ✅ | JWT auth + 9 RBAC roles + permissions seed | Done |
| ✅ | Tenant module: Organization, TenantSettings, SubscriptionPlan, Subscription | Done |
| ✅ | Audit module (immutable log + DB triggers) | Done |
| ✅ | i18n FR / AR / EN with RTL | Done |
| ✅ | Liquibase migrations + dev seed | Done |
| ✅ | Nx Angular monorepo: erp-admin + pos-terminal (PWA) | Done |
| ✅ | Auth shell + login + dashboard + audit + organizations | Done |
| ✅ | Dev Docker Compose stack (PG/Redis/MinIO/MailHog/backend/frontends) | Done |
| ✅ | Prod Docker Compose stack with Traefik + Let's Encrypt | Done |
| ✅ | Hetzner Cloud Terraform IaC | Done |
| ✅ | Observability addon (Prometheus/Loki/Grafana/Tempo) | Done |
| ✅ | CI: backend + frontend + staging + prod deploy | Done |

## Phase 1A — POS MVP (NEXT, ~6–8 weeks)

Goal: a sellable product for small boutiques. After this phase, a single-cash-register shop can run their day on Mini-ERP.

| | Item | Notes |
|---|---|---|
| ⬜ | Catalog module: Product, ProductCategory, Brand, ProductVariant, ProductImage | |
| ⬜ | UoM module: UomCategory, Uom, ProductUom, conversions | Same-category check; ratio math |
| ⬜ | Pricing module: PriceTier, ProductPrice, PriceResolverService | Composite uniqueness; tier per customer |
| ⬜ | Inventory v1: Warehouse, Stock, StockMovement | Single warehouse default; CMP costing |
| ⬜ | POS counter: CashRegister, CashSession, Sale, SaleLine, CashMovement | One open session per register; idempotency key |
| ⬜ | Offline POS: Dexie.js cache + sync queue + idempotent `/pos/sales/sync` | Per spec §3.1.5 |
| ⬜ | Thermal receipt (ESC/POS direct, 58/80 mm) | |
| ⬜ | Notifications stub: SMS adapter (Chinguitel/Mauritel) + email | Stubs in V1 (per ADR-001) |
| ⬜ | Settings UI for tenant admin | |
| ⬜ | Mandatory tests: idempotency, cross-tenant for new entities, sync of 1000 sales | |

## Phase 1B — Sales / Invoicing / Delivery / Payments (~8–10 weeks)

| | Item |
|---|---|
| ⬜ | Sales: Quote, Order, Invoice, CreditNote, DocumentNumberSequence (atomic numbering) |
| ⬜ | Customer module: Customer, Supplier, balances, credits, credit usage |
| ⬜ | Configurable Delivery (3 levels: tenant, module, document) |
| ⬜ | Payments module: Payment, PaymentAllocation across 7 target types, customer credits |
| ⬜ | PDF generation: Thymeleaf + OpenHTMLtoPDF templates per document type |
| ⬜ | Invoice → payment → balance reconciliation |
| ⬜ | VAT (16%) on documents (per ADR-002) |
| ⬜ | Mandatory tests: 100-thread atomic numbering, 5 partial deliveries, payment allocation across N invoices |

## Phase 1C — Advanced Stock / Expiry / Expenses (~6–8 weeks)

| | Item |
|---|---|
| ⬜ | Multi-warehouse + StockTransfer + InventoryCount |
| ⬜ | Lot-expiry module: ProductLot, LotMovement, FEFO selection, ExpiryAlertConfig, ExpiredLotDestruction |
| ⬜ | Scheduled jobs: scanExpiringLots (06:00), markExpiredLots (06:30) |
| ⬜ | Expenses module: ExpenseCategory, Expense (with attachments ≤ 5 MB), recurrence (cron-utils) |
| ⬜ | Reporting V1: aggregate views + ag-Grid dashboards (7 dashboards from §3.10) |
| ⬜ | Mandatory tests: FEFO across successive sales, UoM conversion stability |

## Phase 2 — Reporting / Procurement / V2 features (~8–10 weeks)

| | Item |
|---|---|
| ⬜ | Embedded Metabase (read-replica, signed JWT) |
| ⬜ | Bank-statement reconciliation |
| ⬜ | Promo campaigns (PromoCampaign, PromoCampaignLine) |
| ⬜ | MFA via SMS OTP |
| ⬜ | Advanced email templates (rich HTML) |
| ⬜ | Purchase module complete: PurchaseOrder, PurchaseInvoice |
| ⬜ | Elasticsearch search across catalogue + customers |

## Phase 3 — HR / API / BI (~8–10 weeks)

| | Item |
|---|---|
| ⬜ | HR full: payslips, attendance, advances logic |
| ⬜ | Public REST API (versioned, rate-limited, documented) |
| ⬜ | Accounting export (CSV / accounting-system-specific) |
| ⬜ | Predictive BI |

## Beyond V1

- Read-replica for analytics (Phase 3 → V3)
- Data warehouse (ClickHouse / DuckDB) — V4
- Native mobile POS app (Flutter or Capacitor wrapping PWA) — V4
- Stripe Billing for SaaS subscription collection (per ADR-016)
