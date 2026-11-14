# Inventory service

**Module:** `inventory-service` · **Package root:** `com.petcommerce.inventory` · **Database:** `petplanet_inventory` · **Port:** 8082.

- **Source of truth for availability** of each unique pet: `AVAILABLE`, `RESERVED`, `SOLD`, `RETIRED`. Every change is mirrored to the catalog via `InventoryStatusChangedEvent` (`inventory-status-events`).
- `order-created-events`: reserves **all** pets of the order or none (`@Version` optimistic locking), recording `order_id`, a reservation ID and an expiry time. Emits `PetReservedEvent` (with `totalAmount` and, since Phase 3, the order's `customerId`) or `PetReservationFailedEvent` (`PET_NOT_FOUND`, `PET_ALREADY_RESERVED`, `PET_ALREADY_SOLD`, `PET_RETIRED`). Unknown pets are never created implicitly.
- `payment-succeeded-events`: completes the sale. Pets still `RESERVED` for that order become `SOLD`, then `PetsSoldEvent` (`pets-sold-events`). If none are still reserved (expired or released), nothing is sold and `InventorySaleConflictEvent` is emitted.
- `order-cancelled-events` and `order-failed-events`: release the pets still `RESERVED` for that order (`PetReservationReleasedEvent`). Pets sold or reserved by another order are untouched.
- `pet-catalog-events`: `PetCreatedEvent` creates the inventory row; `PetArchivedEvent` retires an `AVAILABLE` pet.
- `@Scheduled expireReservations()` (every `INVENTORY_EXPIRATION_CHECK_DELAY_MS`, default 30 s) releases reservations older than `INVENTORY_RESERVATION_TIMEOUT` (ISO-8601, default `PT5M`) and emits `PetReservationExpiredEvent`.
- HTTP is read-only: `GET /api/v1/inventory/pets/{petId}` (admin; JWT validated by the shared platform-commons resource server). An unknown pet returns `404` (`PetInventoryNotFoundException`, shared `ApiError` body) instead of a made-up `AVAILABLE` record (changed in Phase 3). The former manual reserve/release/sold endpoints were removed (R-SAGA-07).
