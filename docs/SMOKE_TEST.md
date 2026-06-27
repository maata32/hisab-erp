# Smoke test

Run after `make dev-up` to verify the foundation is healthy. Should take ~3 minutes total.

## 0. Pre-flight

Wait for the backend to finish booting (about 90 s on a fresh image):

```bash
docker compose -f infra/docker/dev/docker-compose.yml logs backend | grep "Started HisabErpApplication"
```

You should see Liquibase output for changesets `0001-extensions` through `0006-seed-permissions`, then app start. The dev seeder logs:

```
Seeded 9 default roles for tenant demo
Dev data seeded. Demo tenant code: 'demo'
  Tenant admin: admin@demo.local / Admin1234!
  Super admin:  root@hisaberp.local / Root12345!
```

## 1. Backend health

```bash
curl -fsS http://localhost:8080/api/v1/health | jq
# {"status":"UP","timestamp":"...","service":"hisab-erp"}

curl -fsS http://localhost:8080/actuator/health | jq
# {"status":"UP", ...}
```

## 2. OpenAPI

```bash
curl -fsS http://localhost:8080/v3/api-docs | jq '.info'
```

Then open the Swagger UI: <http://localhost:8080/swagger-ui.html>

## 3. Login (FR by default)

```bash
curl -fsS -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.local","password":"Admin1234!","tenantCode":"demo"}' | jq
```

Expect:

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "...",
  "accessTokenExpiresInSeconds": 900,
  "user": {
    "userId": "...",
    "tenantId": "...",
    "email": "admin@demo.local",
    "preferredLanguage": "fr",
    "roles": ["TENANT_ADMIN"],
    "permissions": ["organization:read", "user:create", ...]
  }
}
```

Save the `accessToken` and `tenantId`:

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.local","password":"Admin1234!","tenantCode":"demo"}' \
  | jq -r .accessToken)
```

## 4. Whoami

```bash
curl -fsS http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN" | jq
```

## 5. Read own organization

```bash
curl -fsS http://localhost:8080/api/v1/organizations/me \
  -H "Authorization: Bearer $TOKEN" | jq
```

## 6. Read audit log

```bash
curl -fsS "http://localhost:8080/api/v1/audit?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq '.content[] | {action,entityType,occurredAt}'
```

Expect at least an `ORG_CREATED` and a `LOGIN_SUCCESS` event from the seed run.

## 7. Bad credentials

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.local","password":"wrong","tenantCode":"demo"}'
# HTTP/1.1 401 Unauthorized
# {"timestamp":"...","status":401,"code":"auth.bad_credentials","message":"Identifiants invalides", ...}
```

Try with `Accept-Language: en` and you should get `"message":"Invalid credentials"`. With `Accept-Language: ar` you get the Arabic version.

## 8. Cross-tenant isolation (manual quick check)

```bash
docker compose -f infra/docker/dev/docker-compose.yml exec postgres \
  psql -U hisaberp -d hisaberp -c "SELECT code, name, status FROM organizations;"
```

You should see only `demo`. Run the integration test for the rigorous proof:

```bash
cd backend && mvn -B -ntp -pl bootstrap -Dtest=CrossTenantIsolationIT verify
```

## 9. Frontends

- Open <http://localhost:4200>, log in with the same credentials. You should land on the dashboard. The locale switcher in the top-right toggles FR/AR/EN — switch to Arabic and confirm the layout flips RTL.
- Open <http://localhost:4201> for the POS — same login. Tabs for "Sale" and "Session" are placeholders pending Phase 1A.

## 10. Tear down

```bash
make dev-down            # keeps volumes
make dev-nuke            # wipes everything
```
