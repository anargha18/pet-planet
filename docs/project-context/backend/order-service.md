# Order service

**Module:** `order-service` · **Package root:** `com.petcommerce.order` · **Database:** `petplanet_order` · **Port:** 8081.

- Validates requested pets synchronously via `PetCatalogClient` (`GET /api/v1/pets/{petId}`). It has bounded timeouts (`PET_CATALOG_CONNECT_TIMEOUT` default `PT2S`, `PET_CATALOG_READ_TIMEOUT` default `PT5S`) and maps answers: 404 → pet not found (404), not `AVAILABLE` → 409, any other error status, timeout or connection failure → catalog unavailable (503) (R-SVC-04, Phase 4).
- Application services take the caller's `Authentication` on every call. The unauthenticated overloads of `create`, `cancel`, `getOrder` and `getOrders` were removed (R-SVC-04).
- Persists `Order` (`CREATED`) and `OrderItem` snapshots, and publishes `OrderCreatedEvent` (`order-created-events`) via `order_outbox`.
- Status changes go only through `domain/policy/OrderStateMachine` (`CREATED → PAYMENT_PENDING → CONFIRMED`; `CREATED`/`PAYMENT_PENDING → CANCELLED | FAILED`; terminal states never change). Illegal or repeated transitions are ignored, so every saga handler is idempotent.
- Consumes `pet-reserved-events` (`PAYMENT_PENDING`), `pets-sold-events` (`CONFIRMED` → `OrderConfirmedEvent`), `pet-reservation-failed-events`, `payment-failed-events` and `inventory-sale-conflict-events` (`FAILED` → `OrderFailedEvent` with a reason), and `pet-reservation-expired-events` (`CANCELLED` → `OrderCancelledEvent` `RESERVATION_EXPIRED`). It no longer consumes `payment-succeeded-events`.
- Cancellation (`PATCH /api/v1/orders/{orderId}/cancel`) transitions `CREATED` or `PAYMENT_PENDING` orders to `CANCELLED` (409 otherwise) and publishes `OrderCancelledEvent` (`CUSTOMER_CANCELLED`).
- Writes events to `order_outbox` via `messaging/publisher/OrderEventPublisher`; the shared `platform-commons` relay publishes them (see [Event catalog](../events.md)).
- HTTP security: JWT validated by the shared platform-commons resource server (no service-local JWT code); `/api/v1/orders/**` needs `USER` or `ADMIN`; `security/OrderAccessPolicy` limits users to their own orders (the token subject is the customer ID). Errors use the shared `ApiError` body (`api/error/GlobalExceptionHandler` maps not-found → 404, invalid state / pet unavailable → 409, catalog unavailable → 503).
