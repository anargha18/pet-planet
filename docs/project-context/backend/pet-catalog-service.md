# Pet catalog service

**Module:** `pet-catalog-service` · **Package root:** `com.petcommerce.catalog` · **Database:** `petplanet_catalog` (configured name; live database not inspected) · **Port:** 8080 by default.

## Responsibilities and structure

- `controller/PetController`: CRUD and search mappings at `/api/v1/pets`.
- `service/PetService`: transactional create/read/search/update/delete and entity-to-response conversion.
- `repository/PetRepository`: Spring Data persistence and specification-backed search.
- `entity/Pet`, `Species`, `Gender`, `AvailabilityStatus`: catalog entity and enums.
- `dto/CreatePetRequest`, `UpdatePetRequest`, `PetResponse`: HTTP shapes and validation.
- `exception/GlobalExceptionHandler`, `ApiError`: not-found, validation, and generic error responses.
- `config/KafkaConfig`: declares `pet-catalog-events`; no producer or consumer for it was found.

## Behavior

Create defaults availability to `AVAILABLE`. Search supports species, case-insensitive breed substring, minimum/maximum price, availability, and pageable sorting (default page size 10, `createdAt` descending). Update replaces fields and availability; delete is hard delete. DTO validation requires name/species/breed/gender/description, nonnegative age, and price at least 0.01.

`Pet` uses a generated UUID, string enums, timestamps populated through JPA callbacks, and optimistic `@Version`. See [database.md](../database.md) for the Flyway-defined table and ORM comparison.

## Integrations and security

The frontend calls catalog REST APIs. Order-service calls `GET /api/v1/pets/{petId}` via Spring `RestClient`. This service has no visible Spring Security configuration or endpoint access rules. API routes are documented in [api-reference.md](../api-reference.md).

