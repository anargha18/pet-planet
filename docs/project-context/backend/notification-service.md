# Notification service

**Module:** `notification-service` · **Package root:** `com.petcommerce.notification` · **Database:** `petplanet_notification` (configured name; live database not inspected) · **Port:** 8084 by default.

## Responsibilities and structure

- `consumer/NotificationEventConsumer` consumes `order-confirmed-events`.
- `service/NotificationService` handles idempotency, notification persistence, and event publishing.
- `controller/NotificationController` looks up notification by order or customer.
- `entity/Notification`, `NotificationStatus`, `ProcessedEvent`, `NotificationOutboxEvent`.
- Repositories persist notification, processed event, and outbox records.

## Behavior and limits

The handler uses a fixed customer ID, creates a notification record, immediately sets it to `SENT`, and emits `notification-sent-events`. No email, SMS, push, or other delivery provider was found; “sent” describes local record state only. The API returns entity/list representations; see [api-reference.md](../api-reference.md).

The service has no visible Spring Security configuration. Schema details are in [database.md](../database.md).

