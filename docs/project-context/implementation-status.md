# Implementation status

## Feature status (Post-Refactoring)

| Feature | Status | Evidence and qualification |
|---|---|---|
| API Gateway (`gateway-service`) | Implemented | Routes `/auth`, `/users`, `/pets`, `/orders`, `/inventory`, `/payments`, `/notifications`, `/shipments`, `/deliveries`, and `/fulfillment` on `:8089`. |
| User registration/login/RS256 JWT | Implemented | RS256 keypair configured in `.env.example`, `docker-compose.yml`, and service configs. Default `ADMIN` and `CUSTOMER` accounts seeded via Flyway `V2__seed_users.sql`. |
| Pet catalog CRUD/search & sync | Implemented | Seeded with 6 sample pets (`V2__seed_pets.sql`). Consumes `inventory-status-events` to keep `availabilityStatus` synchronized with inventory reservations and sales. |
| Order create/list/get/cancel & Saga | Implemented | Publishes `OrderCreatedEvent` and `OrderCancelledEvent` via transactional outbox; consumes reservation and payment result events to transition order state (`CREATED -> PAYMENT_PENDING -> PAID / FAILED / CANCELLED`). |
| Inventory reserve/expiry/sold | Implemented | Atomically reserves all pets in an order (`@Version` optimistic lock), emits `PetReservedEvent` with order `totalAmount`, releases reservations on payment failure or cancellation, and syncs catalog status. |
| Payment | Implemented | Consumes `pet-reserved-events` using the real order `totalAmount`, publishes payment outcome events, and exposes `GET /api/v1/payments/order/{orderId}`. |
| Notifications & Shipping/Delivery/Fulfillment | Implemented | Propagates real `customerId` from `OrderConfirmedEvent`, exposes `PUT /api/v1/shipments/order/{orderId}/delivered`, and updates existing `FulfillmentRecord` rows on `DELIVERED` transitions. |
| Scheduled Transactional Outbox Poller | Implemented | All event-producing services update `published_at` upon Kafka send completion and run a `@Scheduled` outbox poller (`relayOutboxEvents`) for unpublished events. |
| React Storefront | Implemented | Includes fixed 1-indexed MUI pagination, `?species=` URL query filtering, auto-login on registration, and an Orders & Tracking page (`/orders`). |
