# Fulfillment service — order-tracking projection

**Module:** `fulfillment-service` · **Package root:** `com.petcommerce.fulfillment` · **Database:** `petplanet_fulfillment` · **Port:** 8087.

The **order-tracking read model** (DEC-2, R-SVC-01, CQRS). It builds one denormalized view per order, plus a timeline, purely from events. It writes no other service's data and **emits no events** (topic `fulfillment-updated-events` is retired).

- **Consumes 12 topics**, all in consumer group `fulfillment-tracking-projection`: `order-created`, `pet-reserved`, `pet-reservation-failed`, `payment-succeeded`, `payment-failed`, `payment-refunded`, `order-confirmed`, `order-cancelled`, `order-failed`, `shipment-created`, `delivery-status-updated`, `notification-sent` (`messaging/consumer/OrderTrackingEventConsumer` → `application/OrderTrackingProjector`).
- **Per event, in one transaction:**
  1. Insert the `order_tracking` row if it is absent (`ON CONFLICT DO NOTHING`, so listener threads never race on the primary key).
  2. Lock that row (`PESSIMISTIC_WRITE`).
  3. Insert the timeline row keyed by the event's `eventId` (`ON CONFLICT DO NOTHING`). A `0` result means the event is a duplicate and processing stops here. This is the projection's idempotency, so there is no `processed_events` table.
  4. Apply the change.
- **Monotonic state** (`domain/model/OrderTracking`), so events from different topics may arrive in any order:
  - Order status only moves forward (`CREATED < PAYMENT_PENDING < CONFIRMED/CANCELLED/FAILED`, terminal fixed).
  - Payment goes `PENDING` → outcome; `REFUNDED` always wins.
  - Shipment and delivery statuses never move backwards, and the delivery status also drives the shipment status.
  - `customerId` and `totalAmount` are taken from the first event that carries them.
- **Escalation** (`escalated`, `escalation_reason`):
  - `DELIVERY_FAILED` escalates immediately.
  - `application/EscalationService` (`@Scheduled`, every `FULFILLMENT_ESCALATION_CHECK_MS`, default 60 s) escalates orders still `CREATED`/`PAYMENT_PENDING` longer than `FULFILLMENT_STUCK_AFTER` (default `PT10M`) and adds an `Escalated` timeline row. This "stuck" flag is cleared when the order later closes.
- **Rebuild:** the group was new in Phase 4, so with `auto-offset-reset: earliest` it replayed all retained messages and rebuilt the view from history. Verified 2026-11-15: 16 orders, same status split as order-service. To rebuild again, empty both tables and change the group ID.
- **HTTP** (`FulfillmentController`, read-only):
  - `GET /api/v1/fulfillment/orders/{orderId}` → `TrackingResponse { summary, timeline[] }`, owner or admin. Returns 404 if the projection has not seen the order; an unknown owner is admin-only.
  - `GET /api/v1/fulfillment/orders/me` → page of `TrackingSummaryResponse`.
  - `GET /api/v1/fulfillment/orders?customerId=&escalated=` → page, admin only (`requireAdmin`, 403 otherwise).
  - Pages default to `size=20`, sorted by `updatedAt` descending.
