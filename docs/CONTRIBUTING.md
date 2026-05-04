# Contributing

## Branch & commit conventions

- `main` — production. Protected. Requires PR + green CI + 1 review.
- `develop` — staging. Auto-deploys to staging when staging vars are set.
- `feature/<short-name>` — branch off `develop`, PR back into `develop`.
- `hotfix/<short-name>` — branch off `main`, PR back into both `main` and `develop`.

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(payment): partial allocation across N invoices
fix(pos): handle offline lot reservation race
chore(deps): bump Spring Boot to 3.3.6
docs(arch): clarify RLS bypass policy
```

## Local dev workflow

```bash
make dev-up                        # full stack
# OR
make backend-run                   # Spring Boot locally on :8080
make frontend-admin                # Angular dev server with HMR on :4200
```

Open the Swagger UI at <http://localhost:8080/swagger-ui.html> and tinker. The `dev` profile auto-seeds tenant `demo` + `admin@demo.local / Admin1234!`.

## Adding a new module (Phase 1+)

1. `mkdir -p backend/<module>/src/{main/java/com/minierp/<module>/{api,internal,events},test/java}`
2. Create `backend/<module>/pom.xml` modelled on `backend/identity/pom.xml`
3. Add `<module><module></module>` to `backend/pom.xml`
4. Create `backend/<module>/src/main/java/com/minierp/<module>/package-info.java`:
   ```java
   @org.springframework.modulith.ApplicationModule(
           displayName = "<Module>",
           allowedDependencies = {"shared", "tenant", "identity"})
   package com.minierp.<module>;
   ```
5. Add a `bootstrap/pom.xml` `<dependency>` on the new module
6. Write entities → repos → services → controllers
7. Add a Liquibase changeset under `backend/bootstrap/src/main/resources/db/changelog/<NNNN>-<module>.xml` and reference it from `db.changelog-master.xml`
8. Write at least one IT for cross-tenant isolation
9. Update `ModularityTests.verifies_module_boundaries()` runs automatically — if your new module pulls something not allowed, the test fails.

## Definition of Done

A feature is **done** when:

- [ ] Code compiles, `mvn verify` passes
- [ ] `nx affected -t test --ci` passes
- [ ] Liquibase migrations are reversible (rollback tested locally)
- [ ] Cross-tenant isolation is covered by at least one IT
- [ ] OpenAPI annotations are complete (`@Operation`, `@Tag`, `@RequestBody`)
- [ ] i18n strings added to `messages_fr/ar/en.properties` (backend) and `assets/i18n/fr/ar/en.json` (frontend)
- [ ] Audit events emitted for sensitive operations (CREATE/UPDATE/DELETE/CANCEL/REFUND)
- [ ] A line added to the user-facing CHANGELOG entry of the next release
- [ ] PR description references the spec section number

## Code style

- **Backend**: Lombok for DTO boilerplate; records for value objects; **no field injection** (constructor only via Lombok `@RequiredArgsConstructor`); `@Transactional` on services not on controllers; entities live in `internal/`, never exposed via the REST layer (use mappers).
- **Frontend**: Angular standalone components everywhere; Signals first, then RxJS for streams; `inject()` over constructor injection; OnPush change detection on every leaf component (Phase 1+); strict TypeScript with no `any` (use `unknown` and narrow).

## Tests we want first

The spec's §15.6 "mandatory tests" list:

- Cross-tenant isolation ✅ (Phase 0)
- Atomic invoice numbering under 100 concurrent threads
- 5 successive partial deliveries on same order
- Offline POS sync of 1000 sales
- Idempotency: 10× same request → 1 sale
- Cash session closing with diff calculation
- 100 simultaneous PDF generations
- FEFO selection across successive sales
- Payment allocation across N invoices
- 100 round-trip UoM conversions without numerical drift
- Customer opt-out respected per channel
- Responsive visual tests at 360 / 768 / 1280 px

Each must have a named test method matching `*IT` for ITs and `*Test` for unit tests so CI can find them.
