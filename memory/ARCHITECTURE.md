# Architecture (verified 2026-11-15, Phase 4 working tree)

Brief map only. Canonical docs: `docs/project-context/` (index `README.md`; `architecture.md`, `events.md`, `code-structure.md`, `api-reference.md`, `database.md`, `infrastructure.md`, `security.md`, `testing.md`).

## Runtime

- React/Vite storefront `:5173` → **gateway** `:8089` (only published API). Backend ports are internal to Docker.
- 9 domain services, each with its own PostgreSQL database (one Postgres 16 container) and Flyway: user `:8088`, pet-catalog `:8080`, order `:8081`, inventory `:8082`, payment `:8083`, notification `:8084`, shipping `:8085`, delivery-tracking `:8086`, fulfillment `:8087`.
- Kafka 4.0.1 (KRaft). Optional Kafka UI `:8090` (`make kafka-ui`).
- Run: `make up` / `make reset` (deletes volumes) / `make smoke` / `make test`. Docker VM is ~4 GB; stack uses ~3.1 GB.

## Auth

- user-service: `POST /api/v1/auth/{register,login}` → RS256 JWT (`sub`=userId, `email`, `role`), BCrypt; all requests need gateway's `X-Internal-Service-Token`.
- gateway: validates JWT (public key, shared `PlatformJwt`), coarse role rules only (admin prefixes incl. `PUT /api/v1/deliveries/order/*/status`), CORS in the security chain, denies `/internal/**`.
- All 9 servlet services are resource servers via platform-commons and enforce ownership themselves (403 foreign, admin all). Token `sub` = user ID.

## Events (see `docs/project-context/events.md`)

- `platform-commons`: plain-JSON wire format, listener-type inference, 3 retries → `<topic>.DLT`, MDC `correlationId`, generic **outbox relay** (`FOR UPDATE SKIP LOCKED`, ack before `published_at`).
- 7 producers write outbox rows only (catalog, order, inventory, payment, notification, shipping, delivery). Idempotent consumers use `processed_events` (`INSERT … ON CONFLICT DO NOTHING`); fulfillment uses its timeline `event_id` instead.

## Checkout saga (Phase 2)

`OrderCreated` → inventory reserves all-or-nothing → `PetReserved` → order `PAYMENT_PENDING`; payment charges → `PaymentSucceeded` → inventory sells **only if still reserved** → `PetsSold` → order `CONFIRMED` → `OrderConfirmed` → notification, shipping (`CREATED`) → delivery-tracking (`IN_TRANSIT`) → admin `PUT …/deliveries/order/{id}/status` (`OUT_FOR_DELIVERY` → `DELIVERED`/`DELIVERY_FAILED`) → shipping mirrors, notification informs.
Failures: reservation failed / payment failed / sale conflict → order `FAILED` + `OrderFailed`; customer cancel / expiry → `CANCELLED` + `OrderCancelled`. Inventory releases on those two events; payment refunds (`REFUNDED`) or blocks charging (`closed_orders`, advisory lock).
Order states: `CREATED → PAYMENT_PENDING → CONFIRMED`; `CANCELLED`, `FAILED` terminal (`OrderStateMachine`).

## Downstream (Phase 4)

- notification: typed (8 types), one per order+type; lists per order/customer/`/me`.
- fulfillment: order-tracking projection (12 topics, group `fulfillment-tracking-projection`), `GET /api/v1/fulfillment/orders/{id}` (summary + timeline), `/me`, admin search; escalates delivery failure / stuck orders. Emits nothing.
- Retired topics: `fulfillment-updated-events`, `pet-reservation-released-events`.

## Code structure (enforced)

Per service: `api` (controller/dto/mapper/error) · `application` · `domain` (model/policy/exception) · `security` · `messaging` (event/consumer/publisher) · `persistence` (entity/repository) · `client` · `config`. ArchUnit `ArchitectureTest` (11 rules) per service. Frontend: `app/` (routes, `ProtectedRoute`, `AdminRoute`), `api/` (one axios client, token + 401 interceptors), `features/{auth,catalog,cart,checkout,orders,profile,admin}` with index exports, `components/` (Layout, StatusChip, PageState, format); `npm run check:boundaries`.
