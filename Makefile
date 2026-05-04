# Mini-ERP — common dev commands
# Run `make help` for the full list.

SHELL := /bin/bash
.DEFAULT_GOAL := help

ROOT      := $(shell pwd)
COMPOSE_DEV  := docker compose -f infra/docker/dev/docker-compose.yml
COMPOSE_PROD := docker compose -f infra/docker/prod/docker-compose.yml

# ---------- meta ----------

.PHONY: help
help: ## Show this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)

# ---------- dev stack ----------

.PHONY: dev-up dev-down dev-logs dev-restart dev-rebuild dev-status
dev-up: ## Start full dev stack (Postgres, Redis, MinIO, MailHog, backend, admin, pos)
	$(COMPOSE_DEV) up -d --build
	@echo ""
	@echo "  Backend:    http://localhost:8080  (Swagger: /swagger-ui.html)"
	@echo "  Admin:      http://localhost:4200"
	@echo "  POS:        http://localhost:4201"
	@echo "  Mailpit:    http://localhost:8025"
	@echo "  MinIO:      http://localhost:9001  (minioadmin / minioadmin)"
	@echo "  Postgres:   psql -h localhost -U minierp -d minierp  (pwd: minierp)"
	@echo ""
	@echo "  Demo login (after backend boot): tenant 'demo' / admin@demo.local / Admin1234!"

dev-down: ## Stop dev stack and remove containers
	$(COMPOSE_DEV) down

dev-logs: ## Tail backend logs
	$(COMPOSE_DEV) logs -f backend

dev-restart: ## Restart only the backend container
	$(COMPOSE_DEV) restart backend

dev-rebuild: ## Rebuild backend image and restart
	$(COMPOSE_DEV) up -d --build backend

dev-status: ## Print container status
	$(COMPOSE_DEV) ps

dev-nuke: ## Wipe ALL dev volumes (loses data!)
	$(COMPOSE_DEV) down -v

# ---------- backend ----------

.PHONY: backend-build backend-test backend-test-it backend-run
backend-build: ## Build backend (no tests)
	cd backend && mvn -B -ntp -pl bootstrap -am package -DskipTests

backend-test: ## Backend unit tests
	cd backend && mvn -B -ntp -pl bootstrap -am test

backend-test-it: ## Backend integration tests (Testcontainers)
	cd backend && mvn -B -ntp -pl bootstrap -am verify -Dskip.unit.tests=false

backend-run: ## Run backend locally with dev profile
	cd backend && SPRING_PROFILES_ACTIVE=dev mvn -B -ntp -pl bootstrap spring-boot:run

# ---------- frontend ----------

.PHONY: frontend-install frontend-admin frontend-pos frontend-test frontend-build
frontend-install: ## Install npm deps
	cd frontend && npm install --no-audit --fund=false

frontend-admin: ## Run admin dev server (proxies API to localhost:8080)
	cd frontend && npx nx serve erp-admin

frontend-pos: ## Run POS dev server (proxies API to localhost:8080)
	cd frontend && npx nx serve pos-terminal

frontend-test: ## Run frontend tests (Nx affected)
	cd frontend && npx nx affected -t test --parallel=3 --ci

frontend-build: ## Production build for both apps
	cd frontend && npx nx run-many -t build --configuration=production

# ---------- infra ----------

.PHONY: tf-init tf-plan tf-apply tf-output prod-up prod-pull prod-down obs-up
tf-init: ## terraform init (requires backend config)
	cd infra/terraform && terraform init

tf-plan: ## terraform plan
	cd infra/terraform && terraform plan -var-file=terraform.tfvars

tf-apply: ## terraform apply
	cd infra/terraform && terraform apply -var-file=terraform.tfvars

tf-output: ## Show terraform outputs (server IP, SSH command…)
	cd infra/terraform && terraform output

prod-up: ## Start prod compose on the host you're SSH'd into (requires /etc/minierp/.env)
	$(COMPOSE_PROD) --env-file /etc/minierp/.env up -d --remove-orphans

prod-pull: ## Pull latest prod images
	$(COMPOSE_PROD) --env-file /etc/minierp/.env pull

prod-down: ## Stop prod stack (does NOT remove volumes)
	$(COMPOSE_PROD) --env-file /etc/minierp/.env down

obs-up: ## Start observability addon (Prometheus / Loki / Grafana / Tempo)
	docker compose \
	  -f infra/docker/prod/docker-compose.yml \
	  -f infra/observability/docker-compose.observability.yml \
	  --env-file /etc/minierp/.env up -d

# ---------- quality ----------

.PHONY: fmt lint
fmt: ## Format frontend with Prettier
	cd frontend && npx nx format:write

lint: ## Lint backend (Checkstyle/Spring Modulith) + frontend (ESLint)
	cd backend && mvn -B -ntp -pl bootstrap -am verify -DskipTests
	cd frontend && npx nx run-many -t lint --parallel=3
