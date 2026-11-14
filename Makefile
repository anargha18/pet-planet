# Pet Planet developer commands. Run `make help` for the list.
.DEFAULT_GOAL := help
COMPOSE := docker compose

.PHONY: help up down reset infra kafka-ui build logs ps test frontend-check

help: ## Show available commands
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

up: ## Build and start the whole stack; waits until every service is healthy
	@test -f .env || (cp .env.example .env && echo "Created .env from .env.example")
	$(COMPOSE) up -d --build --wait --wait-timeout 600
	@echo "Storefront: http://localhost:5173   API gateway: http://localhost:8089"

down: ## Stop the stack (keeps database and Kafka data)
	$(COMPOSE) --profile tools down

reset: ## Stop the stack and DELETE all local data (Postgres + Kafka volumes), then start fresh
	$(COMPOSE) --profile tools down -v
	$(MAKE) up

infra: ## Start only Postgres and Kafka (run services from your IDE against them)
	@test -f .env || (cp .env.example .env && echo "Created .env from .env.example")
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

test: ## Run all backend tests (unit + architecture rules) with the Maven wrapper
	./mvnw -B test

frontend-check: ## Build the frontend and check its folder boundaries
	cd frontend && npm run check:boundaries && npm run build
