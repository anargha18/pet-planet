# Order service

**Module:** `order-service` · **Package root:** `com.petcommerce.order` · **Database:** `petplanet_order` · **Port:** 8081.

- Validates requested pets synchronously via `PetCatalogClient` (`GET /api/v1/pets/{petId}`).
- Persists `Order` (`CREATED`) and `OrderItem` snapshots, and publishes `OrderCreatedEvent` (`order-created-events`) via `order_outbox`.
- Status changes go only through `domain/policy/OrderStateMachine` (`CREATED → PAYMENT_PENDING → CONFIRMED`; `CREATED`/`PAYMENT_PENDING → CANCELLED | FAILED`; terminal states never change). Illegal or repeated transitions are ignored, so every saga handler is idempotent.
- Consumes `pet-reserved-events` (`PAYMENT_PENDING`), `pets-sold-events` (`CONFIRMED` → `OrderConfirmedEvent`), `pet-reservation-failed-events`, `payment-failed-events` and `inventory-sale-conflict-events` (`FAILED` → `OrderFailedEvent` with a reason), and `pet-reservation-expired-events` (`CANCELLED` → `OrderCancelledEvent` `RESERVATION_EXPIRED`). It no longer consumes `payment-succeeded-events`.
- Cancellation (`PATCH /api/v1/orders/{orderId}/cancel`) transitions `CREATED` or `PAYMENT_PENDING` orders to `CANCELLED` (409 otherwise) and publishes `OrderCancelledEvent` (`CUSTOMER_CANCELLED`).
- Writes events to `order_outbox` via `messaging/publisher/OrderEventPublisher`; the shared `platform-commons` relay publishes them (see [Event catalog](../events.md)).
