# Notification service

**Module:** `notification-service` · **Package root:** `com.petcommerce.notification` · **Database:** `petplanet_notification` · **Port:** 8084.

Sends **typed** customer notifications (R-SAGA-09). Sending is simulated: each notification is stored and marked `SENT` in the same transaction.

| Consumed topic | Notification type | Wording (`domain/policy/NotificationWording`) |
|---|---|---|
| `order-confirmed-events` | `ORDER_CONFIRMED` | confirmed and paid; "we will let you know when it ships" |
| `order-cancelled-events` | `ORDER_CANCELLED` | as requested (`CUSTOMER_CANCELLED`) or "could not be completed in time" (`RESERVATION_EXPIRED`); any payment will be refunded |
| `order-failed-events` | `ORDER_FAILED` | cause from the reason prefix: payment declined / pet no longer available / no longer reserved |
| `payment-refunded-events` | `PAYMENT_REFUNDED` | refunded amount |
| `shipment-created-events` | `SHIPPED` | tracking number |
| `delivery-status-updated-events` | `OUT_FOR_DELIVERY`, `DELIVERED`, `DELIVERY_FAILED` | `IN_TRANSIT` sends nothing, because it is already covered by `SHIPPED` |

- `OUT_FOR_DELIVERY` and `DELIVERY_FAILED` are two types beyond the plan's six. They exist because delivery-tracking can now report those statuses.
- At most one notification per order and type (unique index `uq_notifications_order_type`), plus `processed_events` deduplication per event.
- A missing `customerId` (for example a refund of a payment created before payment `V4`) is taken from an earlier notification of the same order. If no earlier notification exists, the event fails and is dead-lettered.
- Each notification emits `NotificationSentEvent` (with `customerId` and `notificationType`) via `notification_outbox`. fulfillment consumes it for the timeline.
- HTTP (`NotificationController`, lists of `NotificationResponse` including `type`):
  - `GET /api/v1/notifications/order/{orderId}`: oldest first. Owner or admin. An empty list means none yet; 403 if the notifications belong to another customer.
  - `GET /api/v1/notifications/customer/{customerId}`: newest first, self or admin.
  - `GET /api/v1/notifications/me`: the caller's own.
- JWT is validated by the shared platform-commons resource server. See [security.md](../security.md).
