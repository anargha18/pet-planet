# Delivery tracking service

**Module:** `delivery-tracking-service` · **Package root:** `com.petcommerce.delivery` · **Database:** `petplanet_delivery` (configured name; live database not inspected) · **Port:** 8086 by default.

## Responsibilities and structure

- `consumer/DeliveryTrackingEventConsumer` consumes `shipment-created-events`.
- `service/DeliveryTrackingService` creates delivery records and changes delivery state.
- `controller/DeliveryTrackingController` looks up by order/customer and marks an order delivered.
- `entity/DeliveryRecord`, `DeliveryStatus`, `ProcessedEvent`, `DeliveryOutboxEvent`.
- Repositories persist records, processed event IDs, and outbox entries.
- `config/KafkaConfig` declares shipment-created and delivery-status-updated topics.

## Behavior and limits

Shipment-created creates an `IN_TRANSIT` delivery record and emits `delivery-status-updated-events`. The delivered endpoint changes the status and emits another event. No external carrier/location tracking integration or visible Spring Security configuration exists. See [api-reference.md](../api-reference.md) and [database.md](../database.md).

