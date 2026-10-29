# Inventory service

**Module:** `inventory-service` · **Package root:** `com.petcommerce.inventory` · **Database:** `petplanet_inventory` · **Port:** 8082.

- Consumes `order-created-events` and atomically verifies that all pets in the order are `AVAILABLE` before reserving them (`@Version` optimistic locking) and setting `order_id`.
- Emits `InventoryStatusChangedEvent` (`inventory-status-events`) on every state change (`RESERVED`, `AVAILABLE`, `SOLD`) so `pet-catalog-service` stays synchronized.
- Emits `PetReservedEvent` (carrying `totalAmount`) to trigger `payment-service`.
- Consumes `payment-failed-events` and `order-cancelled-events` to immediately release reserved pets back to `AVAILABLE`.
- `@Scheduled` `expireReservations()` releases expired reservations after 5 minutes and emits `PetReservationExpiredEvent` with the associated `orderId`.
