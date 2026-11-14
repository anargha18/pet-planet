# Implementation status

> **Warning (2026-11-13):** The "Implemented" labels below describe code that exists, not verified end-to-end behavior. The [restructuring audit](../restructuring/01-audit-findings.md) found blockers that stop these features working at runtime. Seeded logins (F1) and browser CORS for auth (F2) were fixed and verified on 2026-11-14 (Phase 0b). Cross-service Kafka delivery (F3) was fixed and verified on 2026-11-14 (Phase 1): a new order reaches `PAID` in about 1 s, with payment, shipment, delivery, fulfillment and notification records and catalog `SOLD`. Compensation paths (cancel, payment failure, expiry) are not re-verified yet. It also found saga gaps (S1, S2, S5, S7, S9). This table will be rewritten as the [restructuring phases](../restructuring/02-target-architecture-and-requirements.md#4-implementation-phases-order-matters) land.

## Feature status (Post-Refactoring)

| Feature | Status | Evidence and qualification |
|---|---|---|
| API Gateway (`gateway-service`) | Implemented | Publishes the application API, validates RS256 JWTs (user-service issues them), applies edge role checks, and routes requests to backend services. |
| User management and authentication | Implemented | User-service owns BCrypt credentials, integer-ID user CRUD, and RS256 token issuance; the gateway only validates tokens. User accounts support self profile/password updates, admin CRUD, and soft deactivation; default `ADMIN` and `USER` accounts are seeded/migrated by Flyway. |
| Internal service boundary | Implemented for local Compose | Backend HTTP ports are not published; user-service requests require a shared gateway token. Production mTLS is still an infrastructure deployment requirement, not configured here. |
| Pet catalog CRUD/search & sync | Implemented | Seeded with 6 sample pets (`V2__seed_pets.sql`). Consumes `inventory-status-events` to keep `availabilityStatus` synchronized with inventory reservations and sales. |
| Order create/list/get/cancel & Saga | Implemented | Publishes `OrderCreatedEvent` and `OrderCancelledEvent` via transactional outbox; consumes reservation and payment result events to transition order state (`CREATED -> PAYMENT_PENDING -> PAID / FAILED / CANCELLED`). |
| Inventory reserve/expiry/sold | Implemented | Atomically reserves all pets in an order (`@Version` optimistic lock), emits `PetReservedEvent` with order `totalAmount`, releases reservations on payment failure or cancellation, and syncs catalog status. |
| Payment | Implemented | Consumes `pet-reserved-events` using the real order `totalAmount`, publishes payment outcome events, and exposes `GET /api/v1/payments/order/{orderId}`. |
| Notifications & Shipping/Delivery/Fulfillment | Implemented | Propagates real `customerId` from `OrderConfirmedEvent`, exposes `PUT /api/v1/shipments/order/{orderId}/delivered`, and updates existing `FulfillmentRecord` rows on `DELIVERED` transitions. |
| Transactional outbox + Kafka conventions | Implemented and verified (2026-11-14) | All 8 producing services (now including catalog) write outbox rows only. The shared `platform-commons` relay publishes after broker acknowledgement. Consumers use plain JSON, retry, then dead-letter to `<topic>.DLT` (verified live with a poison message), and deduplicate via `processed_events`. See [Event catalog](./events.md). |
| React Storefront | Implemented | Includes fixed 1-indexed MUI pagination, `?species=` URL query filtering, auto-login on registration, and an Orders & Tracking page (`/orders`). |
