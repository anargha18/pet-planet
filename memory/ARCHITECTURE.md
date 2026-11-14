# Architecture (verified 2026-11-14, HEAD `499990a`)

Brief map only. Canonical docs: `docs/project-context/` (index `README.md`; `architecture.md`, `events.md`, `code-structure.md`, `api-reference.md`, `database.md`, `infrastructure.md`, `security.md`, `testing.md`).

## Runtime

- React/Vite storefront `:5173` → **gateway** `:8089` (only published API). Backend ports are internal to Docker.
- 9 domain services, each with its own PostgreSQL database (one Postgres 16 container) and Flyway: user `:8088`, pet-catalog `:8080`, order `:8081`, inventory `:8082`, payment `:8083`, notification `:8084`, shipping `:8085`, delivery-tracking `:8086`, fulfillment `:8087`.
- Kafka 4.0.1 (KRaft). Optional Kafka UI `:8090` (`make kafka-ui`).
- Run: `make up` / `make reset` (deletes volumes) / `make smoke` / `make test`. Docker VM is ~4 GB; stack uses ~3.1 GB.

## Auth

- user-service: `POST /api/v1/auth/{register,login}` → RS256 JWT (`sub`=email, `userId`, `role`), BCrypt; all requests need gateway's `X-Internal-Service-Token`.
- gateway: validates JWT (public key), coarse role rules, single `CorsWebFilter` (`FRONTEND_ORIGIN`), forwards `X-Authenticated-User-*`. Still calls order-service to check order ownership for tracking GETs (to be removed in Phase 3).
- order, catalog, inventory re-verify JWT with hand-rolled `JwtService`; payment/notification/shipping/delivery/fulfillment have no security (Phase 3).

## Events (see `docs/project-context/events.md`)

- `platform-commons`: plain-JSON wire format, listener-type inference, 3 retries → `<topic>.DLT`, MDC `correlationId`, generic **outbox relay** (`FOR UPDATE SKIP LOCKED`, ack before `published_at`).
- 8 producers write outbox rows only (catalog, order, inventory, payment, notification, shipping, delivery, fulfillment). Idempotent consumers use `processed_events` (`INSERT … ON CONFLICT DO NOTHING`).

## Checkout saga (Phase 2)

`OrderCreated` → inventory reserves all-or-nothing → `PetReserved` → order `PAYMENT_PENDING`; payment charges → `PaymentSucceeded` → inventory sells **only if still reserved** → `PetsSold` → order `CONFIRMED` → `OrderConfirmed` → notification, shipping → delivery-tracking → fulfillment.
Failures: reservation failed / payment failed / sale conflict → order `FAILED` + `OrderFailed`; customer cancel / expiry → `CANCELLED` + `OrderCancelled`. Inventory releases on those two events; payment refunds (`REFUNDED`) or blocks charging (`closed_orders`, advisory lock).
Order states: `CREATED → PAYMENT_PENDING → CONFIRMED`; `CANCELLED`, `FAILED` terminal (`OrderStateMachine`).

## Code structure (enforced)

Per service: `api` (controller/dto/mapper/error) · `application` · `domain` (model/policy/exception) · `security` · `messaging` (event/consumer/publisher) · `persistence` (entity/repository) · `client` · `config`. ArchUnit `ArchitectureTest` (11 rules) per service. Frontend: `app/`, `api/`, `features/{auth,catalog,cart,checkout,orders}` with index exports, `components/`; `npm run check:boundaries`.
