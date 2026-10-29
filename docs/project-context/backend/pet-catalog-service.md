# Pet catalog service

**Module:** `pet-catalog-service` · **Package root:** `com.petcommerce.catalog` · **Database:** `petplanet_catalog` · **Port:** 8080.

- Provides CRUD and filtered pagination on `/api/v1/pets` (`GET` public; mutations require `ROLE_ADMIN`).
- Seeded with 6 initial pets in `V2__seed_pets.sql`.
- Publishes `PetCreatedEvent` (`pet-catalog-events`) when new pets are added and consumes `InventoryStatusChangedEvent` (`inventory-status-events`) via `PetCatalogEventConsumer` to keep `availabilityStatus` synchronized with `inventory-service`.
