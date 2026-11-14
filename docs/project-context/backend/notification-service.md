# Notification service

**Module:** `notification-service` · **Package root:** `com.petcommerce.notification` · **Database:** `petplanet_notification` · **Port:** 8084.

- Consumes `order-confirmed-events`, persists a `Notification` for the order's `customerId`, marks it `SENT`, and emits `NotificationSentEvent` via `notification_outbox`.
- HTTP (via `NotificationController`, returns `NotificationResponse`, never the entity): `GET /api/v1/notifications/order/{orderId}` (owner or admin; 404 if none) and `GET /api/v1/notifications/customer/{customerId}` (self or admin). JWT validated by the shared platform-commons resource server; ownership checked in `NotificationService` (403 for other customers). See [security.md](../security.md).
