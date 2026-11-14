# Database design

## Overview

- PostgreSQL 16 (`postgres:16-alpine`) hosts nine service-specific databases (`petplanet_catalog`, `petplanet_order`, `petplanet_inventory`, `petplanet_payment`, `petplanet_notification`, `petplanet_shipping`, `petplanet_delivery`, `petplanet_fulfillment`, `petplanet_user`) initialized via `infrastructure/postgres/init/01-create-catalog-database.sh`.
- Flyway manages all schema migrations (`classpath:db/migration`), and all services run with `spring.jpa.hibernate.ddl-auto: validate`.
- **Timestamp standardization**: All timestamp columns across all nine databases use `TIMESTAMPTZ` to align with `java.time.Instant` in Hibernate 6.
- **Seed data migrations**:
  - `user-service` (`V2__seed_users.sql`): Seeds the default admin and regular-user accounts with password `password123`; `V3__integer_user_ids_and_account_status.sql` migrates the regular account role from `CUSTOMER` to `USER`.
  - `pet-catalog-service` (`V2__seed_pets.sql`): Seeds 6 sample pets (`11111111-1111-1111-1111-111111111101` .. `106`) with `AVAILABLE` status.
  - `inventory-service` (`V3__seed_inventory.sql`): Seeds the matching 6 `pet_inventory` rows with `AVAILABLE` status.

## Key schema additions

- `pet_inventory` (in `inventory-service` `V1__init_inventory.sql`) includes `order_id BIGINT` (indexed via `idx_pet_inventory_order_id`) so reservations are linked to their originating order for compensating rollbacks on payment failure, order cancellation, or reservation expiry.
- Eight outbox tables (`catalog_outbox`, `order_outbox`, `inventory_outbox`, `payment_outbox`, `notification_outbox`, `shipment_outbox`, `delivery_outbox`, `fulfillment_outbox`) are read by the shared `platform-commons` outbox relay. `published_at TIMESTAMPTZ` is set only after the broker acknowledges the send. Phase 1 migrations added `message_key VARCHAR(255)` (Kafka key chosen by the publisher), `attempts INTEGER NOT NULL DEFAULT 0` and `last_error TEXT` (relay failure tracking), plus a partial index on `created_at WHERE published_at IS NULL`:
  - `order-service` `V3__outbox_relay_columns.sql`; `inventory-service` `V4__outbox_relay_columns.sql`;
  - `payment-service` `V2__outbox_relay_columns.sql`, which also adds `correlation_id UUID`;
  - `notification-service`, `shipping-service`, `delivery-tracking-service` and `fulfillment-service`: `V2__outbox_relay_columns.sql` each;
  - `pet-catalog-service` `V3__catalog_outbox.sql` creates `catalog_outbox` (`id`, `event_id` unique, `topic`, `event_type`, `pet_id`, `correlation_id`, `message_key`, `payload`, `created_at`, `published_at`, `attempts`, `last_error`).
- `processed_events (event_id UUID PK, processed_at)` in inventory, payment, notification, shipping, delivery-tracking and fulfillment is written with `INSERT … ON CONFLICT (event_id) DO NOTHING` (`ProcessedEventRepository.markProcessed`) inside the consumer's transaction.
- `user-service` `V4__fix_seed_account_passwords.sql` replaces the broken V2 seed hash with a verified hash of `password123`.
- Phase 2 (saga correctness):
  - `order-service` `V4__confirmed_replaces_paid.sql`: `PAID` was removed from the order lifecycle, so existing `PAID` orders become `CONFIRMED`. Statuses are `CREATED`, `PAYMENT_PENDING`, `CONFIRMED`, `CANCELLED`, `FAILED`.
  - `payment-service` `V3__refunds_and_closed_orders.sql`: adds `payments.refunded_at TIMESTAMPTZ` and `payments.refund_reason VARCHAR(64)` (status `REFUNDED`). New table `closed_orders (order_id BIGINT PK, reason VARCHAR(64) NOT NULL, closed_at TIMESTAMPTZ NOT NULL)` marks cancelled/failed orders so they are never charged later.
  - No migration was needed for the new `RETIRED` status (`pet_inventory.status`, `pets.availability_status` are `VARCHAR(20)`).
- `user-service` migration `V3` replaces the UUID `users.id` primary key with an auto-generated `INTEGER` identity column and adds `active BOOLEAN NOT NULL DEFAULT TRUE` for soft deactivation. Password hashes remain private to the user service and are never included in API responses.
- Order/event/customer projections continue to store `customerId` as `BIGINT`, but new values are the actual generated user ID widened to `Long`; the previous email-hash-derived values are no longer produced. Existing local orders cannot be reliably mapped from their old hashes to user accounts; use a fresh local database volume if old order data must be cleared.
