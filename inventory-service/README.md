# Inventory Service

This module manages unique pet availability in its own PostgreSQL database and intentionally does not query the catalog database.

## Concurrency choice

The inventory table uses optimistic locking via the `version` column (`@Version`). A pet is a unique item with only one unit, so a race condition is possible when two order events try to reserve the same pet at the same time. With optimistic locking, both transactions read the same row, but only the first successful commit updates the row version. The second transaction then fails with an optimistic locking exception and gets rolled back instead of double-reserving the same pet.

This is a good fit because the change is small, fast, and stateful. We avoid pessimistic locking for the whole table and keep the database concurrency model simple.

## Events

- Consumes: `order-created-events`
- Produces: `pet-reserved-events`, `pet-reservation-failed-events`, `pet-reservation-released-events`, `pet-reservation-expired-events`

## Status values

- `AVAILABLE`
- `RESERVED`
- `SOLD`

## Phase 7 hardening

- Reservation lifecycle tracks `AVAILABLE -> RESERVED -> SOLD` and `AVAILABLE -> RESERVED -> AVAILABLE` when a reservation is released.
- Each reservation gets a unique `reservationId`, distinct from the order ID.
- Reservation expiry is configurable via `inventory.reservation-expiration-minutes` and is checked by the scheduled expiry task.
- Duplicate `OrderCreatedEvent` payloads are ignored by storing the `eventId` in `processed_events` before business handling.
- Inventory state transitions rely on optimistic locking (`@Version`) so two concurrent reservations cannot both succeed against the same pet.
- Outbox entries are saved with the inventory change so the database transaction and event publication are separated cleanly.
