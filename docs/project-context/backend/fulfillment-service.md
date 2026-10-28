# Fulfillment service

**Module:** `fulfillment-service` · **Package root:** `com.petcommerce.fulfillment` · **Database:** `petplanet_fulfillment` (configured name; live database not inspected) · **Port:** 8087 by default.

## Responsibilities and structure

- `consumer/FulfillmentEventConsumer` consumes `delivery-status-updated-events`.
- `service/FulfillmentService` maps delivery status into a fulfillment record and emits an update event.
- `controller/FulfillmentController` looks up records by order/customer.
- `entity/FulfillmentRecord`, `FulfillmentStatus`, `ProcessedEvent`, `FulfillmentOutboxEvent`.
- Repositories persist projection, processed event IDs, and outbox records.
- `config/KafkaConfig` declares delivery-status-updated and fulfillment-updated topics.

## Behavior and limits

`IN_TRANSIT` maps to `SHIPPED`, `DELIVERED` to `DELIVERED`, and other values to `ESCALATED`. If a record already exists for an order, the handler returns it without applying later status changes. It is an event-derived local projection, not an integrated operations platform. No visible Spring Security configuration exists. See [api-reference.md](../api-reference.md) and [database.md](../database.md).

