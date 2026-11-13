# Pet Planet

Event-driven pet e-commerce platform built with Java 17, Spring Boot 3.5.5, Spring Cloud Gateway, PostgreSQL 16, Apache Kafka 4.0.1 (KRaft), and React 18 / Vite.

## Architecture Highlights

- **API Gateway (`gateway-service`, `:8089`)**: The only published application API entrypoint. It validates RS256 JWTs, applies edge role checks, handles CORS, and routes requests to private backend services.
- **User management (`user-service`, internal `:8088`)**: Owns account records, BCrypt password hashes, login/registration, and RS256 token issuance (the only service with the private key), plus user/profile CRUD and soft deactivation.
- **Choreographed Checkout Saga with Compensating Rollbacks**:
  1. `order-service` (`:8081`) validates catalog availability, snapshots pet pricing, saves the order (`CREATED`), and publishes `OrderCreatedEvent` via its transactional outbox (`order-created-events`).
  2. `inventory-service` (`:8082`) atomically reserves all pets in the order using optimistic locking (`@Version`), synchronizes `pet-catalog-service` (`:8080`) via `inventory-status-events`, and emits `PetReservedEvent` (`pet-reserved-events`).
  3. `payment-service` (`:8083`) processes the exact order `totalAmount` and publishes `PaymentSucceededEvent` or `PaymentFailedEvent`.
  4. On payment success, `order-service` transitions the order to `PAID` and publishes `OrderConfirmedEvent` (`order-confirmed-events`), triggering `inventory-service` (marks `SOLD`), `notification-service` (`:8084`), and `shipping-service` (`:8085`) -> `delivery-tracking-service` (`:8086`) -> `fulfillment-service` (`:8087`).
  5. On reservation failure, 5-minute reservation expiry, payment decline, or order cancellation, compensating events automatically roll back inventory reservations and update catalog availability.
- **Transactional Outbox Relay + Idempotent Consumers**: Every event-producing service persists outbox rows in the local PostgreSQL transaction, marks `published_at` upon Kafka broker acknowledgment, and runs a `@Scheduled` outbox poller for retry resilience alongside `processed_events` deduplication tables.

## Quickstart

Prerequisites: Docker Desktop with **at least 4 GB of memory (6 GB recommended)**, plus `make`. JDK 17 is only needed to run tests or services outside Docker. Maven is not needed; the repo ships a Maven wrapper.

```sh
make up        # creates .env from .env.example if missing, builds, starts, and waits until every service is healthy
make ps        # container status and health
make logs s=order-service   # follow one service's logs
make down      # stop (keeps data)
```

**First run after pulling these changes:** run `make reset` once. It deletes the local Postgres and Kafka volumes (all local data) and starts fresh. Earlier commits edited already-applied database migrations, so old volumes fail Flyway validation.

Other commands: `make infra` (Postgres + Kafka only, to run services from your IDE), `make test` (all backend tests), `make frontend-check` (frontend build + folder-boundary check), `make help`.

### Seeded Accounts & Sample Catalog

Flyway migrations automatically seed **6 sample pets** (3 dogs, 3 cats) and **2 default accounts**:

- **User Account**: `customer@petplanet.com` / `password123`
- **Admin Account**: `admin@petplanet.com` / `password123`

### Endpoints

- **React Storefront**: `http://localhost:5173`
- **API Gateway**: `http://localhost:8089`
- **Application APIs**: `http://localhost:8089/api/v1/...` (backend service ports are not published by Compose)
