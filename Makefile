# Pet Planet developer commands. Run `make help` for the list.
.DEFAULT_GOAL := help
COMPOSE := docker compose

.PHONY: help keys up down reset seed infra kafka-ui build logs ps test verify smoke ui-check e2e frontend-check

help: ## Show available commands
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

keys: ## Create .env and generate local secrets (JWT key pair, internal token); keeps existing ones
	scripts/generate-dev-keys.sh

up: keys ## Build and start the whole stack; waits until every service is healthy
	$(COMPOSE) up -d --build --wait --wait-timeout 600
	@echo "Storefront: http://localhost:5173   API gateway: http://localhost:8089"

down: ## Stop the stack (keeps database and Kafka data)
	$(COMPOSE) --profile tools down

reset: ## Stop the stack and DELETE all local data (Postgres + Kafka volumes), then start fresh
	$(COMPOSE) --profile tools down -v
	$(MAKE) up

seed: ## Add demo data to the running stack: 8 more pets, customer john@petplanet.com, 3 orders at different delivery stages
	scripts/seed-demo.sh

infra: keys ## Start only Postgres and Kafka (run services from your IDE against them)
	$(COMPOSE) up -d --wait --wait-timeout 300 postgres kafka
	@echo "Postgres: localhost:5432   Kafka: localhost:9092"

kafka-ui: ## Start Kafka UI (topics, messages, dead-letter topics) at http://localhost:8090
	$(COMPOSE) --profile tools up -d kafka-ui
	@echo "Kafka UI: http://localhost:8090"

build: ## Build all Docker images
	$(COMPOSE) build

logs: ## Follow logs (all services, or one: make logs s=order-service)
	$(COMPOSE) logs -f --tail=200 $(s)

ps: ## Show container status and health
	$(COMPOSE) ps

test: ## Run all backend tests (unit, slice, architecture, Testcontainers; needs Docker) with the Maven wrapper
	./mvnw -B test

verify: ## Like test, plus packaging every module (the restructuring's acceptance build)
	./mvnw -B verify

smoke: ## End-to-end checks against the running stack (restarts payment/inventory/fulfillment with test settings)
	scripts/smoke-test.sh

ui-check: ## Headless-Chrome walkthrough of every customer/admin page against the running stack (needs Chrome; CHROME_PATH)
	cd frontend && npm install --no-audit --no-fund --silent && npm run ui-check

e2e: ## Playwright end-to-end tests against the running stack (installs Playwright's Chromium on first run); guide: docs/13-playwright-e2e.md
	cd frontend && npm install --no-audit --no-fund --silent && npx playwright install chromium && npm run e2e

frontend-check: ## Build the frontend and check its folder boundaries
	cd frontend && npm run check:boundaries && npm run build
