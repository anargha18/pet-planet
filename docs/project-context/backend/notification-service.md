# Notification service

**Module:** `notification-service` · **Package root:** `com.petcommerce.notification` · **Database:** `petplanet_notification` · **Port:** 8084.

- Consumes `order-confirmed-events`, persists a `Notification` for the order's `customerId`, marks it `SENT`, and emits `NotificationSentEvent` via `notification_outbox`.
