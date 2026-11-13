# Implementation status

> **Warning (2026-11-13):** The "Implemented" labels below describe code that exists, not verified end-to-end behavior. The [restructuring audit](../restructuring/01-audit-findings.md) found blockers that stop these features working at runtime: seeded logins fail (F1), browser auth is blocked by CORS (F2), and cross-service Kafka events can't be deserialized (F3). It also found saga gaps (S1, S2, S5, S7, S9). This table will be rewritten as the [restructuring phases](../restructuring/02-target-architecture-and-requirements.md#4-implementation-phases-order-matters) land.

## Feature status (Post-Refactoring)

| Feature | Status | Evidence and qualification |
|---|---|---|
| API Gateway (`gateway-service`) | Implemented | Publishes the application API, handles register/login, issues and validates RS256 JWTs, applies edge role checks, and routes requests to backend services. |
| User management and authentication | Implemented | User-service owns BCrypt credentials and integer-ID user CRUD; gateway owns JWTs. User accounts support self profile/password updates, admin CRUD, and soft deactivation; default `ADMIN` and `USER` accounts are seeded/migrated by Flyway. |
| Internal service boundary | Implemented for local Compose | Backend HTTP ports are not published; user-service requests require a shared gateway token. Production mTLS is still an infrastructure deployment requirement, not configured here. |
| Pet catalog CRUD/search & sync | Implemented | Seeded with 6 sample pets (`V2__seed_pets.sql`). Consumes `inventory-status-events` to keep `availabilityStatus` synchronized with inventory reservations and sales. |
| Order create/list/get/cancel & Saga | Implemented | Publishes `OrderCreatedEvent` and `OrderCancelledEvent` via transactional outbox; consumes reservation and payment result events to transition order state (`CREATED -> PAYMENT_PENDING -> PAID / FAILED / CANCELLED`). |
| Inventory reserve/expiry/sold | Implemented | Atomically reserves all pets in an order (`@Version` optimistic lock), emits `PetReservedEvent` with order `totalAmount`, releases reservations on payment failure or cancellation, and syncs catalog status. |
| Payment | Implemented | Consumes `pet-reserved-events` using the real order `totalAmount`, publishes payment outcome events, and exposes `GET /api/v1/payments/order/{orderId}`. |
| Notifications & Shipping/Delivery/Fulfillment | Implemented | Propagates real `customerId` from `OrderConfirmedEvent`, exposes `PUT /api/v1/shipments/order/{orderId}/delivered`, and updates existing `FulfillmentRecord` rows on `DELIVERED` transitions. |
| Scheduled Transactional Outbox Poller | Implemented | All event-producing services update `published_at` upon Kafka send completion and run a `@Scheduled` outbox poller (`relayOutboxEvents`) for unpublished events. |
| React Storefront | Implemented | Includes fixed 1-indexed MUI pagination, `?species=` URL query filtering, auto-login on registration, and an Orders & Tracking page (`/orders`). |
