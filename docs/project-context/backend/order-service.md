# Order service

**Module:** `order-service` · **Package root:** `com.petcommerce.order` · **Database:** `petplanet_order` · **Port:** 8081.

- Validates requested pets synchronously via `PetCatalogClient` (`GET /api/v1/pets/{petId}`).
- Persists `Order` (`CREATED`) and `OrderItem` snapshots, and publishes `OrderCreatedEvent` (`order-created-events`) via `order_outbox`.
- Consumes `pet-reserved-events` (`PAYMENT_PENDING`), `pet-reservation-failed-events` (`FAILED`), `pet-reservation-expired-events` (`CANCELLED`), `payment-succeeded-events` (`PAID` -> publishes `OrderConfirmedEvent`), and `payment-failed-events` (`FAILED`).
- Cancellation (`PATCH /api/v1/orders/{orderId}/cancel`) transitions `CREATED` or `PAYMENT_PENDING` orders to `CANCELLED` and publishes `OrderCancelledEvent`.
- Writes events to `order_outbox` via `messaging/publisher/OrderEventPublisher`; the shared `platform-commons` relay publishes them (see [Event catalog](../events.md)).
