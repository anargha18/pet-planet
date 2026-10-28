# Shipping service

**Module:** `shipping-service` · **Package root:** `com.petcommerce.shipping` · **Database:** `petplanet_shipping` (configured name; live database not inspected) · **Port:** 8085 by default.

## Responsibilities and structure

- `consumer/ShippingEventConsumer` consumes `order-confirmed-events`.
- `service/ShippingService` creates shipment records and tracking numbers.
- `controller/ShippingController` looks up shipments by order/customer.
- `entity/Shipment`, `ShipmentStatus`, `ProcessedEvent`, `ShipmentOutboxEvent`.
- Repositories persist shipment, processed event, and outbox records.
- `config/KafkaConfig` declares order-confirmed and shipment-created topics.

## Behavior and limits

The handler uses a fixed customer ID, assigns a generated tracking number, persists a shipment, changes it to `SHIPPED`, and emits `shipment-created-events`. No carrier, label, address, or shipping-rate integration was found. There is no visible Spring Security configuration. See [api-reference.md](../api-reference.md) and [database.md](../database.md).

