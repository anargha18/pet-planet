# Pet catalog service

**Module:** `pet-catalog-service` · **Package root:** `com.petcommerce.catalog` · **Database:** `petplanet_catalog` · **Port:** 8080.

- Provides CRUD and filtered pagination on `/api/v1/pets` (`GET` public; mutations require `ROLE_ADMIN`).
- Seeded with 6 initial pets in `V2__seed_pets.sql`.
- Publishes `PetCreatedEvent` (`pet-catalog-events`) when new pets are added and consumes `InventoryStatusChangedEvent` (`inventory-status-events`) via `PetCatalogEventConsumer` to keep `availabilityStatus` synchronized with `inventory-service`.
- `availabilityStatus` is a read-only copy of inventory's state (`AVAILABLE`, `RESERVED`, `SOLD`, `RETIRED`). `PUT` cannot change it (R-SAGA-05).
- `DELETE /api/v1/pets/{petId}` **archives** an `AVAILABLE` pet: `RETIRED`, `PetArchivedEvent` via `catalog_outbox`, hidden from the default listing. `RESERVED`/`SOLD` pets return `409` (`PetNotArchivableException`). Rows are never hard-deleted, so order snapshots remain valid.
- Catalog events (`PetCreatedEvent`, `PetArchivedEvent`) go through the transactional outbox (`catalog_outbox`).
- HTTP security: JWT validated by the shared platform-commons resource server (no service-local JWT code); errors use the shared `ApiError` body (not found → 404, not archivable → 409).
