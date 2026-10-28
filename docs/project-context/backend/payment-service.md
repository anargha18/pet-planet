# Payment service

**Module:** `payment-service` · **Package root:** `com.petcommerce.payment` · **Database:** `petplanet_payment` (configured name; live database not inspected) · **Port:** 8083 by default.

## Responsibilities and structure

- `consumer/PaymentEventConsumer`: listens to `pet-reserved-events`.
- `service/PaymentService`: idempotency, simulated outcome, persistence, result event creation.
- `entity/Payment`, `PaymentStatus`, `ProcessedEvent`, `PaymentOutboxEvent`.
- Repositories persist payments, processed event IDs, and outbox entries.
- `config/KafkaConfig`: declares reservation and payment result topics.
- `event`: reservation and payment success/failure records.

## Behavior and limits

Payment is a simulation, not an external processor. It stores one payment per order, uses a fixed amount and USD currency, generates a synthetic transaction reference, and selects success/failure from `PAYMENT_SIMULATION_RESULT`. It emits `payment-succeeded-events` or `payment-failed-events`; order-service consumes them.

There is no REST controller, payment provider integration, or customer payment credential handling. Payment API surface is therefore event-only. See [database.md](../database.md), [architecture.md](../architecture.md), and [implementation-status.md](../implementation-status.md).

