# Backend services

## Shared stack

All nine backend modules use Java 17 and Spring Boot 3.5.5, with a service-specific Maven `pom.xml` (no root Maven aggregator). Common dependencies include Spring Boot Actuator, Spring Data JPA, Validation, Web, Spring Kafka, Flyway Core plus its PostgreSQL extension, PostgreSQL JDBC, springdoc OpenAPI 2.8.13, Lombok (except user-service), and Spring Boot Test. User-service additionally declares Spring Security, JJWT 0.12.6, and Spring Security Test.

Each service has its own `src/main/resources/application.yml`, Flyway migrations at `src/main/resources/db/migration`, and conventional package layout under `com.petcommerce`: `controller`, `service`, `repository`, `entity`, with `dto`, `event`, `consumer`, `client`, `config`, and `exception` where relevant. Application configuration enables Flyway and uses Hibernate `ddl-auto: validate`; `spring.jpa.open-in-view` is false. Actuator exposes `health` and `info`. The live behavior of these settings was not run or verified.

## Service documentation

| Service | Notes |
|---|---|
| [Pet catalog](./pet-catalog-service.md) | Catalog CRUD/search and pet persistence |
| [Order](./order-service.md) | Order API, synchronous catalog client, lifecycle events |
| [Inventory](./inventory-service.md) | Unique-pet reservation, expiry, idempotency |
| [Payment](./payment-service.md) | Simulated event-driven payments |
| [Notification](./notification-service.md) | Notification records and lookup |
| [Shipping](./shipping-service.md) | Shipment creation and tracking numbers |
| [Delivery tracking](./delivery-tracking-service.md) | Delivery state and manual delivered operation |
| [Fulfillment](./fulfillment-service.md) | Fulfillment projection from delivery events |
| [User](./user-service.md) | Registration, authentication, and user endpoints |

See the top-level [API reference](../api-reference.md), [database notes](../database.md), [architecture](../architecture.md), and [security](../security.md) for cross-service details.

## Shared persistence and transaction conventions

- Each service has a separately configured datasource/database; service migrations do not declare cross-service foreign keys.
- Flyway owns schema creation; Hibernate validates rather than creates/updates schema.
- Mutations commonly use `@Transactional`; read operations commonly use `readOnly = true`.
- Event-producing methods generally persist outbox payloads and schedule Kafka sends after commit. No polling relay that updates `published_at` was found.
- Enum values use string persistence where annotated. Timestamp columns vary between timezone-aware and timezone-less SQL types; live Hibernate validation has not been performed.
- UUIDs are used for pets and most projections; order IDs and payment internal IDs are numeric identity values. The user primary key is UUID, unlike order `customer_id` (BIGINT).
- Entity/migration details and known mismatches are centralized in [database.md](../database.md).

