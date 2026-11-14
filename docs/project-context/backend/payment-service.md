# Payment service

**Module:** `payment-service` · **Package root:** `com.petcommerce.payment` · **Database:** `petplanet_payment` · **Port:** 8083.

- `pet-reserved-events`: charges the order's `totalAmount` through `application/PaymentGateway`. A missing or non-positive amount is rejected, so the event is retried and then dead-lettered; there is no fallback amount. Emits `PaymentSucceededEvent` or `PaymentFailedEvent`.
- `order-cancelled-events` and `order-failed-events`: records the order in `closed_orders` and refunds a `SUCCEEDED` payment (`REFUNDED`, `refunded_at`, `refund_reason`, then `PaymentRefundedEvent` on `payment-refunded-events`). A `PetReserved` handled after the order is closed is never charged.
- A charge and the closing of the same order are serialized with a PostgreSQL advisory lock (`PaymentRepository.lockOrder`, `pg_advisory_xact_lock`): whichever runs second sees the other's result.
- `client/SimulatedPaymentGateway` (DEC-4, no real money): `PAYMENT_SIMULATION_RESULT` = `SUCCESS` (default) or `FAILURE`; `PAYMENT_SIMULATION_DELAY` (ISO-8601, default `PT0S`) makes charges slow, so the smoke test can reproduce cancel/expiry races.
- Events go through `payment_outbox`, published by the shared `platform-commons` relay.
- Exposes `GET /api/v1/payments/order/{orderId}` via `PaymentController`.
