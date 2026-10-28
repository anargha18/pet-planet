# Inventory service

**Module:** `inventory-service` · **Package root:** `com.petcommerce.inventory` · **Database:** `petplanet_inventory` (configured name; live database not inspected) · **Port:** 8082 by default.

## Responsibilities and structure

- `controller/InventoryController`: status, reserve, release, and sold operations.
- `service/InventoryService`: reservation transitions, event handling, expiration scheduler.
- `entity/PetInventory`, `InventoryStatus`, `ProcessedEvent`, `InventoryOutboxEvent`.
- `repository/PetInventoryRepository`, `ProcessedEventRepository`, `InventoryOutboxRepository`.
- `consumer/InventoryEventConsumer`: listens for order-created and order-confirmed events.
- `event`: order-created/confirmed and reservation success/failure/released/expired records.
- `config/KafkaConfig`: declares order-created, order-confirmed, and pet-reserved topics.

## Behavior and persistence

One inventory row represents one unique pet. A missing row is treated as available in code; inventory is not seeded/synchronized from catalog. Reservation sets `RESERVED`, a unique reservation ID, timestamps, and an expiry. A scheduled check reopens expired reservations. Confirmation marks known inventory sold. Optimistic `@Version` protects concurrent updates; `processed_events` supports event-idempotency.

The controller's status read returns an `AVAILABLE` response for missing inventory without persisting it. Manual reserve generates a UUID; release and sold operations require an existing row. See [database.md](../database.md) for migration schema.

## Messaging, transactions, gaps

Consumes `order-created-events` and `order-confirmed-events`; emits `pet-reserved-events`, `pet-reservation-failed-events`, `pet-reservation-released-events`, and `pet-reservation-expired-events`. No order-created event producer or consumers for the failure/release/expiry topics were found. No Spring Security configuration is present. API details are in [api-reference.md](../api-reference.md).

