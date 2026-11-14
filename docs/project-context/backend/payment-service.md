# Payment service

**Module:** `payment-service` · **Package root:** `com.petcommerce.payment` · **Database:** `petplanet_payment` · **Port:** 8083.

- Consumes `pet-reserved-events` and processes payment for the order's actual `totalAmount` (falling back to `1000.00` only if `totalAmount` is null in legacy tests).
- Emits `PaymentSucceededEvent` or `PaymentFailedEvent` via `payment_outbox`, published by the shared `platform-commons` relay.
- Exposes `GET /api/v1/payments/order/{orderId}` via `PaymentController`.
