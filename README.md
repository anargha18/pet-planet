# Pet Planet

Event-driven pet e-commerce platform built with Java 17, Spring Boot 3.5.5, Spring Cloud Gateway, PostgreSQL 16, Apache Kafka 4.0.1 (KRaft), and React 18 / Vite.

## Architecture Highlights

- **API Gateway (`gateway-service`, `:8089`)**: Unified entrypoint routing `/api/v1/auth`, `/api/v1/users`, `/api/v1/pets`, `/api/v1/orders`, `/api/v1/inventory`, `/api/v1/payments`, `/api/v1/notifications`, `/api/v1/shipments`, `/api/v1/deliveries`, and `/api/v1/fulfillment`.
- **Stateless RS256 JWT Authentication (`user-service`, `:8088`)**: Issues RSA-signed JWTs (`JWT_PRIVATE_KEY`) verified independently by downstream services (`JWT_PUBLIC_KEY`).
- **Choreographed Checkout Saga with Compensating Rollbacks**:
  1. `order-service` (`:8081`) validates catalog availability, snapshots pet pricing, saves the order (`CREATED`), and publishes `OrderCreatedEvent` via its transactional outbox (`order-created-events`).
  2. `inventory-service` (`:8082`) atomically reserves all pets in the order using optimistic locking (`@Version`), synchronizes `pet-catalog-service` (`:8080`) via `inventory-status-events`, and emits `PetReservedEvent` (`pet-reserved-events`).
  3. `payment-service` (`:8083`) processes the exact order `totalAmount` and publishes `PaymentSucceededEvent` or `PaymentFailedEvent`.
  4. On payment success, `order-service` transitions the order to `PAID` and publishes `OrderConfirmedEvent` (`order-confirmed-events`), triggering `inventory-service` (marks `SOLD`), `notification-service` (`:8084`), and `shipping-service` (`:8085`) -> `delivery-tracking-service` (`:8086`) -> `fulfillment-service` (`:8087`).
  5. On reservation failure, 5-minute reservation expiry, payment decline, or order cancellation, compensating events automatically roll back inventory reservations and update catalog availability.
- **Transactional Outbox Relay + Idempotent Consumers**: Every event-producing service persists outbox rows in the local PostgreSQL transaction, marks `published_at` upon Kafka broker acknowledgment, and runs a `@Scheduled` outbox poller for retry resilience alongside `processed_events` deduplication tables.

## Quickstart

```sh
cp .env.example .env
docker compose up -d --build
docker compose ps
```

### Seeded Accounts & Sample Catalog

Flyway migrations automatically seed **6 sample pets** (3 dogs, 3 cats) and **2 default accounts**:

- **Customer Account**: `customer@petplanet.com` / `password123`
- **Admin Account**: `admin@petplanet.com` / `password123`

### Endpoints

- **React Storefront**: `http://localhost:5173`
- **API Gateway**: `http://localhost:8089`
- **Pet Catalog Service**: `http://localhost:8080/api/v1/pets`
- **Order Service**: `http://localhost:8081/api/v1/orders`
- **Inventory Service**: `http://localhost:8082/api/v1/inventory`
- **Payment Service**: `http://localhost:8083/api/v1/payments/order/{orderId}`
- **Notification Service**: `http://localhost:8084/api/v1/notifications`
- **Shipping Service**: `http://localhost:8085/api/v1/shipments`
- **Delivery Tracking Service**: `http://localhost:8086/api/v1/deliveries`
- **Fulfillment Service**: `http://localhost:8087/api/v1/fulfillment`
- **User Service**: `http://localhost:8088/api/v1/auth` and `/api/v1/users`
