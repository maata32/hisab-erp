# Architecture Decision Records

This file logs every choice made where the spec (`cahier_des_charges_hisab_erp_v3.docx`) was ambiguous, contradictory, or silent. Each decision is reversible — open an issue if you want to revisit.

Last updated: 2026-05-03.

---

## ADR-001 — Cloud target: Hetzner Cloud

**Spec said:** "VPS + Docker Compose" for V1, "managed Kubernetes" for V2+. No specific provider.

**Decision:** Hetzner Cloud (Falkenstein, EU). Hetzner-managed PostgreSQL when GA in target region, otherwise self-hosted PG on a dedicated CX22 node. Self-hosted Redis, MinIO, Traefik on the app node.

**Why:** Cost (~€20-50/mo for ~50 tenants) and EU latency to Mauritania is acceptable. Provider lock-in is low (Terraform + Docker Compose are portable).

**Reversible?** Yes — IaC isolates provider concerns to `infra/terraform/`.

---

## ADR-002 — VAT / tax handling (gap in spec)

**Spec gap:** No `taxRate` / `taxAmount` on invoice lines. Mauritania VAT is 16%.

**Decision:**
- Add `TaxRate` entity (per tenant): `code`, `name`, `rate Decimal(5,4)`, `isDefault`, `appliesTo` ∈ {SALE, PURCHASE, BOTH}, `validFrom`, `validUntil`.
- Add `taxRateId` (nullable FK), `taxRate Decimal(5,4)` (snapshot), `taxAmount Decimal(15,2)` to `SaleLine`, `InvoiceLine`, `QuoteLine`, `OrderLine`, `PurchaseInvoiceLine`.
- Add `subTotal`, `taxTotal`, `total` to all document headers.
- Tenant-level toggle `invoiceSettings.taxEnabled` (default `true`, default rate 16% MRU).
- Prices on `Product` and `ProductPrice` are **tax-exclusive** by default; tenant can switch to tax-inclusive via `invoiceSettings.pricesIncludeTax`.

**Why:** Cannot ship a commercial ERP without VAT — it's a legal hard requirement in Mauritania.

---

## ADR-003 — GDPR / RGPD compliance scaffolding (gap in spec)

**Spec gap:** Not mentioned. SaaS handling personal data must address it.

**Decision (V1 scaffolding, full compliance is V2):**
- Document data-retention defaults: `Customer` PII retained 10 years (commercial law), `AuditLog` retained 5 years then archived to cold storage, `User.password` hashed with Argon2id.
- Endpoint stubs (501 Not Implemented in V1, full impl in V2): `POST /api/v1/gdpr/data-export-request`, `POST /api/v1/gdpr/erasure-request`.
- Consent flag `Customer.marketingConsent boolean` and `consentUpdatedAt` already in scope via notification opt-out.
- All log statements MUST go through `LogSanitizer.sanitize(...)` which masks emails, phones, national IDs.

**Why:** Avoid building a GDPR-hostile model now and being unable to retrofit.

---

## ADR-004 — Payment allocation: 7 target types (resolves spec inconsistency)

**Spec inconsistency:** §3.6.2 lists 5 types; §15.1 lists 7.

**Decision:** Implement all 7: `SALE_INVOICE`, `PURCHASE_INVOICE`, `CUSTOMER_BALANCE`, `SALE`, `CUSTOMER_CREDIT`, `EXPENSE`, `SALARY`. Polymorphic FK is implemented as `(targetType ENUM, targetId UUID)` with no DB-level FK constraint; integrity enforced at service layer with explicit dispatch.

**Why:** Expenses and salaries pass through Payment per the spec's HR / Expense modules — they need allocation targets.

---

## ADR-005 — `Payment.amount` invariant: deferred trigger, not CHECK

**Spec inconsistency:** Said "CHECK SUM(...) = amount" but standard Postgres CHECK can't reference aggregates of another table.

**Decision:** Implement as a `DEFERRABLE INITIALLY DEFERRED` constraint trigger on `payment_allocation` (INSERT/UPDATE/DELETE) that recomputes `SUM(allocated_amount)` per `payment_id` and raises if it differs from `payment.amount`. Service layer also enforces this in a single transaction. Tested explicitly in integration tests.

**Why:** Triggers are the only way to do cross-row invariants in Postgres without a CHECK on a materialized aggregate column.

---

## ADR-006 — Cost valuation default: CMP (weighted average)

**Spec said:** "CMP **or** FIFO" — no default.

**Decision:** Default `CMP` (weighted average cost). Configurable per tenant via `pricingSettings.costMethod` ∈ `CMP | FIFO`. **No mid-life migration** — once a tenant has stock movements, the choice is locked. Migration tool deferred to V2.

**Why:** CMP is operationally simpler, matches Mauritanian retail accounting practice, and avoids per-lot cost layers in V1.

---

## ADR-007 — Auto-allocation strategy vocabulary: FIFO / LIFO / MANUAL

**Spec inconsistency:** Three vocabularies (`FIFO|LIFO|OLDEST_FIRST` vs `FIFO|LIFO|MANUAL`).

**Decision:** Final enum is `AutoAllocationStrategy { FIFO, LIFO, MANUAL }`. `OLDEST_FIRST` is a synonym for `FIFO` (same result on `dueDate ASC`). Default `FIFO`.

---

## ADR-008 — Currency handling: single-currency per tenant in V1

**Spec gap:** Default MRU but no FX model.

**Decision:** Each tenant operates in **one** currency (tenant-level `currency` field). Multi-currency / FX is V3+. All money columns: `Decimal(15, 2)`. We never convert at runtime in V1. If a tenant needs USD invoices, they switch their entire tenant — not an in-flight conversion.

---

## ADR-009 — Customer credit-limit enforcement: warn-by-default, manager override

**Spec gap:** What happens when a sale exceeds `creditLimit`?

**Decision:**
- Setting `paymentSettings.creditLimitBehavior` ∈ `WARN | BLOCK | OFF`. Default `WARN`.
- `WARN`: sale proceeds, frontend shows banner, audit log entry created.
- `BLOCK`: sale rejected with HTTP 422; only roles `MANAGER` or `TENANT_ADMIN` can override (header `X-Override-Credit-Limit: true` + reason).
- `OFF`: limit ignored.

---

## ADR-010 — Recurring expenses: use `cron-utils` for RRULE-like parsing

**Spec gap:** Said "RRULE iCal-like" but no library.

**Decision:** Use `com.cronutils:cron-utils:9.2.1` with a custom `RecurrenceRule` type. Supported frequencies in V1: `MONTHLY`, `QUARTERLY`, `ANNUAL`. Daily/weekly deferred to V2.

**Why:** True iCal RRULE (`ical4j`) is overkill for monthly/quarterly/annual — `cron-utils` is lighter.

---

## ADR-011 — Customer/Supplier opening balances: import on tenant onboarding

**Spec gap:** No process to capture historical receivables/payables.

**Decision:** During tenant onboarding wizard:
1. Endpoint `POST /api/v1/customers/{id}/opening-balance` accepts `{ amount, asOfDate, reference }`.
2. Creates a synthetic `OPENING_BALANCE` `Payment` (or anti-payment if customer owes) with `targetType=CUSTOMER_BALANCE`.
3. Locked: only callable while `Organization.status='TRIAL'` OR by `SUPER_ADMIN`.

---

## ADR-012 — Delivery driver: extend RBAC + Employee link

**Spec gap:** `Delivery.deliveredBy` is a User but no DRIVER role.

**Decision:**
- Add 10th role: `DRIVER`. Gets read access to assigned deliveries + write `markAsDelivered`.
- `Delivery.deliveredBy` may reference any User, but assignment requires the user to have `DRIVER` permission OR `MANAGER` override.
- Route planning is V3 — not in this scope.

---

## ADR-013 — AuditLog retention: 5 years hot, then S3 cold archive

**Decision:**
- Hot retention: 5 years in PostgreSQL.
- Daily job archives logs older than 5 years to MinIO/S3 as gzipped JSON, then deletes from PG.
- Restore is manual: ops loads the archive into a read-only `audit_log_archive` table.

---

## ADR-014 — Domain & SSL (placeholder)

**Spec gap:** Uses `hisaberp.mr` placeholders — ownership unconfirmed.

**Decision:** Terraform and compose use **`${ROOT_DOMAIN}`** env var (default `hisaberp.local` for dev). Real domain is wired via `.env.prod` at deploy time. Traefik handles Let's Encrypt automatically.

---

## ADR-015 — Document numbering: `SELECT FOR UPDATE` on `document_number_sequence`

**Decision:** Confirmed per spec. One row per `(tenantId, documentType, year)` with `currentValue BIGINT`. Numbering service:
1. `SELECT ... FOR UPDATE` the row (creates if missing).
2. Increment, format as `{prefix}-{year}-{padded current}`.
3. Commit.

Backed by integration test that hammers 100 concurrent threads and asserts no gaps and no duplicates.

---

## ADR-016 — Subscription billing of the platform itself: Stripe (V2)

**Spec gap:** How does the platform collect from tenants?

**Decision:** V1 is manual / offline (admin marks subscriptions as paid). V2 integrates Stripe Billing. Stub interface `PlatformBillingProvider` exists in V1 with a `ManualBillingProvider` impl.

---

## ADR-017 — Database = single PG with RLS, no per-tenant schema

**Decision:** Confirmed per spec. Single DB, single schema, every tenant-aware table has `tenant_id UUID NOT NULL`, with **both** Hibernate `@Filter` (defense layer 1) **and** Postgres RLS (defense layer 2). Backed by integration test that disables Hibernate filter and asserts RLS still blocks the cross-tenant query.

---

## ADR-018 — Java 21 LTS, Spring Boot 3.3.x, Spring Modulith 1.2.x

**Decision:** Locked to Java 21 (LTS), Spring Boot 3.3.5, Spring Modulith 1.2.4. Java 22+ deferred until next LTS (Java 25, expected 2025-09 — already available at the assistant knowledge cutoff but conservative pick for prod).

---

## ADR-019 — Frontend: Angular 18 LTS over 17

**Spec said:** "Angular 17+".

**Decision:** Angular 18 (current LTS at decision time, supports the same standalone-component model). Tailwind v3.4. PrimeNG 17.x (compatible with Angular 18). Nx 19.x.

---

## ADR-020 — Encryption-at-rest for PII fields: pgcrypto-based column encryption

**Decision (additive to spec):** Encrypt `Employee.nationalId`, `Employee.iban`, `User.mfaSecret` with `pgp_sym_encrypt` and a key from env (`PII_ENCRYPTION_KEY`, rotated yearly). All other PII (name, phone, email) relies on disk encryption + RLS.

**Why:** National IDs and bank details are highest-risk PII; column-level encryption hardens against backup leak.

---

## Outstanding ambiguities resolved later

- Cancellation reason taxonomy → free-text + optional `cancellationReasonCode` enum, codes seeded per tenant.
- Cross-system data sharing (Customer email same as User email?) → independent fields, no auto-link in V1.
- Notification dispatch listener naming → method is `dispatch(...)`; spec example was a typo.
