# Database design

## Overview

- PostgreSQL 16 (`postgres:16-alpine`) hosts nine service-specific databases (`petplanet_catalog`, `petplanet_order`, `petplanet_inventory`, `petplanet_payment`, `petplanet_notification`, `petplanet_shipping`, `petplanet_delivery`, `petplanet_fulfillment`, `petplanet_user`) initialized via `infrastructure/postgres/init/01-create-catalog-database.sh`.
- Flyway manages all schema migrations (`classpath:db/migration`), and all services run with `spring.jpa.hibernate.ddl-auto: validate`.
- **Timestamp standardization**: All timestamp columns across all nine databases use `TIMESTAMPTZ` to align with `java.time.Instant` in Hibernate 6.
- **Seed data migrations**:
  - `user-service` (`V2__seed_users.sql`): Seeds `admin@petplanet.com` (`ADMIN`) and `customer@petplanet.com` (`CUSTOMER`) with password `password123`.
  - `pet-catalog-service` (`V2__seed_pets.sql`): Seeds 6 sample pets (`11111111-1111-1111-1111-111111111101` .. `106`) with `AVAILABLE` status.
  - `inventory-service` (`V3__seed_inventory.sql`): Seeds the matching 6 `pet_inventory` rows with `AVAILABLE` status.

## Key schema additions

- `pet_inventory` (in `inventory-service` `V1__init_inventory.sql`) includes `order_id BIGINT` (indexed via `idx_pet_inventory_order_id`) so reservations are linked to their originating order for compensating rollbacks on payment failure, order cancellation, or reservation expiry.
- All seven outbox tables (`order_outbox`, `inventory_outbox`, `payment_outbox`, `notification_outbox`, `shipment_outbox`, `delivery_outbox`, `fulfillment_outbox`) track `published_at TIMESTAMPTZ`, which is populated when the event is sent to Kafka or relayed by the `@Scheduled` outbox poller.
