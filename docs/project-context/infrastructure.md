# Infrastructure and local development

## Docker Compose topology

`docker-compose.yml` defines PostgreSQL, Kafka, nine backend containers, and frontend on the named `pet-planet` network.

| Service | Image/build | Host port | Persistent volume / health |
|---|---|---:|---|
| PostgreSQL | `postgres:16-alpine` | 5432 (configurable) | `postgres-data`; `pg_isready` health check |
| Kafka | `apache/kafka:4.0.1` | 9092 (configurable) | `kafka-data`; broker API health check |
| Pet catalog | Local service Dockerfile | 8080 | No application health check |
| Order | Local service Dockerfile | 8081 | No application health check |
| Inventory | Local service Dockerfile | 8082 | No application health check |
| Payment | Local service Dockerfile | 8083 | No application health check |
| Notification | Local service Dockerfile | 8084 | No application health check |
| Shipping | Local service Dockerfile | 8085 | No application health check |
| Delivery tracking | Local service Dockerfile | 8086 | No application health check |
| Fulfillment | Local service Dockerfile | 8087 | No application health check |
| User | Local service Dockerfile | 8088 | No application health check |
| Frontend | `frontend/Dockerfile`, Node 20 Alpine/Vite dev server; build context `frontend/` | 5173 | No persistent volume or health check |

Each backend Dockerfile builds with Maven 3.9.11 / Temurin 17, skips tests, then runs the jar on Temurin 17 JRE as a non-root `app` user. `frontend/Dockerfile` runs `npm ci` and the Vite development server; it is not a static production serving image. `frontend/.dockerignore` excludes local env files, node modules, build output, and Git metadata from that build context.

PostgreSQL initialization mounts `infrastructure/postgres/init` read-only. `01-create-catalog-database.sh` creates service-specific roles and databases using environment variables. Standard Postgres initialization scripts only run on a newly initialized data volume; actual behavior was not exercised. Flyway initializes service tables at application startup.

Kafka runs a single KRaft broker/controller with Docker listener `kafka:29092`, host listener 9092, plaintext protocol, and local persistence. Topic `NewTopic` declarations use one partition/replica. These are local-development settings, not production deployment hardening.

## Dependencies and startup

Prerequisites indicated by manifests/configuration: Docker with Compose, Java 17, Maven (or use service Dockerfiles), and Node.js compatible with Node 20 container.

Commands are derived from files and **were not run or verified**:

```sh
cp .env.example .env
# Configure local-only credentials and secrets in .env; never commit them.
docker compose up -d --build
docker compose ps
```

Individual service, from that module directory:

```sh
mvn spring-boot:run
```

Frontend outside containers, from the repository root:

```sh
cd frontend
npm ci
npm run dev
npm run build
npm run preview
```

Backend tests/builds are inferred Maven commands from each module:

```sh
mvn test
mvn package
```

The Compose command starts application containers as well as infrastructure; it is not an infrastructure-only command. `docker compose down -v` removes named data volumes and is destructive; it is not required for normal shutdown.

## Ports and dependency ordering

| Component | Default host port |
|---|---:|
| PostgreSQL | 5432 |
| Kafka | 9092 |
| Catalog | 8080 |
| Order | 8081 |
| Inventory | 8082 |
| Payment | 8083 |
| Notification | 8084 |
| Shipping | 8085 |
| Delivery tracking | 8086 |
| Fulfillment | 8087 |
| User | 8088 |
| Vite frontend | 5173 |

Most backends wait for PostgreSQL health; Kafka consumers/producers wait for Kafka health. Order additionally waits for catalog service startup. Frontend depends on catalog, order, and user services but not on their health. Actuator health is configured in backend apps, but Compose does not define backend health checks.

## Environment variables (names only)

Secret values are intentionally omitted. `.env.example` contains local variable names; use local ignored `.env` and replace any sample/default credentials and JWT key with suitable local values.

| Purpose | Variables |
|---|---|
| Bootstrap PostgreSQL | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `POSTGRES_PORT` |
| Per-service DB naming/credentials | `CATALOG_DATABASE_NAME`, `CATALOG_DATABASE_USERNAME`, `CATALOG_DATABASE_PASSWORD`; the matching `ORDER_`, `INVENTORY_`, `PAYMENT_`, `NOTIFICATION_`, `SHIPPING_`, `DELIVERY_`, `FULFILLMENT_`, and `USER_` `DATABASE_NAME`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` |
| Service/host ports | `PET_CATALOG_PORT`, `ORDER_SERVICE_PORT`, `INVENTORY_SERVICE_PORT`, `PAYMENT_SERVICE_PORT`, `NOTIFICATION_SERVICE_PORT`, `SHIPPING_SERVICE_PORT`, `DELIVERY_SERVICE_PORT`, `FULFILLMENT_SERVICE_PORT`, `USER_SERVICE_PORT`, `FRONTEND_PORT` |
| Kafka | `KAFKA_PORT`, `KAFKA_ADVERTISED_HOST`, `KAFKA_BOOTSTRAP_SERVERS` |
| Payment simulation | `PAYMENT_SIMULATION_RESULT` |
| JWT/CORS | `JWT_SECRET`, `JWT_EXPIRATION_MS`, `FRONTEND_ORIGIN`, `APP_CORS_ALLOWED_ORIGIN` |
| Frontend API URLs | `VITE_PET_CATALOG_URL`, `VITE_ORDER_URL`, `VITE_USER_URL` |
| Spring database URLs | `CATALOG_DATABASE_URL`, `ORDER_DATABASE_URL`, `INVENTORY_DATABASE_URL`, `PAYMENT_DATABASE_URL`, `NOTIFICATION_DATABASE_URL`, `SHIPPING_DATABASE_URL`, `DELIVERY_DATABASE_URL`, `FULFILLMENT_DATABASE_URL`, `USER_DATABASE_URL` |
| Other Spring configuration | `SERVER_PORT`, `PET_CATALOG_URL`, `INVENTORY_RESERVATION_EXPIRATION_MINUTES`, `INVENTORY_EXPIRATION_CHECK_DELAY_MS` |

Compose supplies some service-side variables and defaults not explicitly listed in `.env.example`. The exact resolved environment and `.env` values were not inspected/documented. Never expose or commit credentials.

## External infrastructure and operating limitations

No cloud deployment manifests, production deployment configuration, external database, object storage, payment processor, email service, carrier, or external identity system was found. Database and Kafka health checks exist, but service health checks/readiness orchestration are absent. No verified seed data was found; a clean catalog may require creating pets before browsing. Database init behavior, startup success, migration success, and all commands remain unverified.
