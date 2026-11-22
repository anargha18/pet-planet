# 06 · Database

## Database per service

```mermaid
flowchart TB
  subgraph PG["One PostgreSQL 16 container (local only)"]
    direction LR
    D1[("petplanet_user")]
    D2[("petplanet_catalog")]
    D3[("petplanet_order")]
    D4[("petplanet_inventory")]
    D5[("petplanet_payment")]
    D6[("petplanet_notification")]
    D7[("petplanet_shipping")]
    D8[("petplanet_delivery")]
    D9[("petplanet_fulfillment")]
  end
  user-service --> D1
  pet-catalog-service --> D2
  order-service --> D3
  inventory-service --> D4
  payment-service --> D5
  notification-service --> D6
  shipping-service --> D7
  delivery-tracking-service --> D8
  fulfillment-service --> D9
```

- Each domain service owns **one database with its own role and password**. No service reads another's tables. Data that crosses boundaries travels in events, and each service keeps the copy it needs (for example `customer_id` on payments, shipments and notifications).
- The databases and roles are created by `infrastructure/postgres/init/01-create-catalog-database.sh` from the `*_DATABASE_NAME`, `*_DATABASE_USERNAME` and `*_DATABASE_PASSWORD` variables. It runs **only on an empty data volume**; after changing it, run `make reset` (deletes all local data).
- Sharing one container is a local-development convenience. The services would work unchanged against separate servers.
- **Flyway** owns every schema (`classpath:db/migration`), and Hibernate runs with `ddl-auto: validate`, so the entities must match the migrations.
- **Never edit an applied migration.** Add a new `V<n>__description.sql` instead. (Early commits did edit applied migrations; an old local volume that fails Flyway's checksum check needs one `make reset`.)
- All timestamps are `TIMESTAMPTZ`, mapped to `java.time.Instant`.
- Superuser in Compose is `petplanet_admin`: `docker exec -it pet-planet-postgres psql -U petplanet_admin -d petplanet_order`.

## Cross-service references (logical, not foreign keys)

There are no foreign keys between databases. These IDs link records logically and are carried by events:

```mermaid
flowchart LR
  U["users.id (INTEGER)"] -. "customer_id" .-> O["orders"]
  U -. customer_id .-> PY["payments"] & N["notifications"] & S["shipments"] & D["delivery_records"] & T["order_tracking"]
  P["pets.id (UUID)"] -. "pet_id" .-> OI["order_items"] & INV["pet_inventory"]
  O -. "order_id (BIGINT)" .-> INV & PY & N & S & D & T
```

## ER diagrams per service

### user-service (`petplanet_user`)

```mermaid
erDiagram
  users {
    integer id PK "identity"
    varchar email UK
    varchar password_hash "BCrypt, never returned"
    varchar first_name
    varchar last_name
    varchar role "USER or ADMIN"
    boolean active "soft deactivation"
    timestamptz created_at
    timestamptz updated_at
  }
```

### pet-catalog-service (`petplanet_catalog`)

```mermaid
erDiagram
  pets {
    uuid id PK
    varchar name
    varchar species "DOG or CAT"
    varchar breed
    integer age "at least 0"
    varchar gender "MALE or FEMALE"
    numeric price "above 0"
    varchar description
    varchar availability_status "copy of inventory"
    timestamptz created_at
    timestamptz updated_at
    bigint version
  }
  catalog_outbox {
    uuid id PK
    uuid event_id UK
    varchar topic
    varchar event_type
    uuid pet_id
    varchar message_key
    text payload
    timestamptz published_at
    integer attempts
    text last_error
  }
```

Indexes on `species`, `LOWER(breed)`, `price`, `availability_status`.

### order-service (`petplanet_order`)

```mermaid
erDiagram
  orders ||--|{ order_items : contains
  orders {
    bigint id PK "bigserial"
    bigint customer_id "user id from token"
    varchar status "CREATED to CONFIRMED etc"
    numeric total_amount
    timestamptz created_at
    timestamptz updated_at
    bigint version
  }
  order_items {
    bigint id PK
    bigint order_id FK
    uuid pet_id
    varchar pet_name "snapshot"
    varchar pet_species "snapshot"
    varchar pet_breed "snapshot"
    numeric unit_price "snapshot"
    integer quantity "always 1"
    numeric subtotal
  }
  order_outbox {
    uuid id PK
    uuid event_id UK
    varchar topic
    bigint order_id
    uuid correlation_id
    text payload
    timestamptz published_at
  }
```

Item snapshots keep an order correct even if the pet's price or description changes later.

### inventory-service (`petplanet_inventory`)

```mermaid
erDiagram
  pet_inventory {
    uuid id PK
    uuid pet_id UK
    varchar status "AVAILABLE RESERVED SOLD RETIRED"
    bigint order_id "reserving or buying order"
    uuid reservation_id
    timestamptz reserved_at
    timestamptz reservation_expires_at
    timestamptz sold_at
    bigint version "optimistic lock"
    timestamptz created_at
    timestamptz updated_at
  }
  processed_events {
    uuid event_id PK
    timestamptz processed_at
  }
  inventory_outbox {
    uuid id PK
    uuid event_id UK
    varchar topic
    uuid pet_id
    bigint order_id
    text payload
    timestamptz published_at
  }
```

### payment-service (`petplanet_payment`)

```mermaid
erDiagram
  payments {
    bigint id PK
    uuid payment_id UK
    bigint order_id UK "one payment per order"
    bigint customer_id "null before V4"
    numeric amount
    varchar currency "USD"
    varchar status "PROCESSING SUCCEEDED FAILED REFUNDED"
    varchar transaction_reference
    timestamptz refunded_at
    varchar refund_reason
    timestamptz created_at
    timestamptz updated_at
    bigint version
  }
  closed_orders {
    bigint order_id PK
    varchar reason
    timestamptz closed_at
  }
  processed_events {
    uuid event_id PK
    timestamptz processed_at
  }
  payment_outbox {
    uuid id PK
    uuid event_id UK
    bigint order_id
    uuid payment_id
    text payload
    timestamptz published_at
  }
```

`closed_orders` is a tombstone: once an order is cancelled or failed, a later `PetReserved` for it is never charged.

### notification-service (`petplanet_notification`)

```mermaid
erDiagram
  notifications {
    uuid id PK
    bigint order_id
    bigint customer_id
    varchar type "8 types, unique per order"
    text message
    varchar status "SENT"
    timestamptz created_at
    timestamptz sent_at
  }
  processed_events {
    uuid event_id PK
    timestamptz processed_at
  }
  notification_outbox {
    uuid id PK
    uuid event_id UK
    bigint order_id
    text payload
    timestamptz published_at
  }
```

Unique index `uq_notifications_order_type (order_id, type)`; check constraint `chk_notifications_type`.

### shipping-service (`petplanet_shipping`)

```mermaid
erDiagram
  shipments {
    uuid id PK
    bigint order_id UK
    bigint customer_id
    varchar status "CREATED SHIPPED DELIVERED DELIVERY_FAILED"
    varchar tracking_number UK
    timestamptz created_at
    timestamptz shipped_at
    timestamptz delivered_at
  }
  processed_events {
    uuid event_id PK
    timestamptz processed_at
  }
  shipment_outbox {
    uuid id PK
    uuid event_id UK
    bigint order_id
    text payload
    timestamptz published_at
  }
```

### delivery-tracking-service (`petplanet_delivery`)

```mermaid
erDiagram
  delivery_records ||--|{ delivery_events : timeline
  delivery_records {
    uuid id PK
    bigint order_id UK
    bigint customer_id
    varchar tracking_number
    varchar status "IN_TRANSIT OUT_FOR_DELIVERY DELIVERED DELIVERY_FAILED"
    uuid correlation_id "saga id reused for admin events"
    timestamptz created_at
    timestamptz updated_at
    timestamptz delivered_at
  }
  delivery_events {
    uuid id PK
    uuid delivery_id FK
    bigint order_id
    varchar status
    varchar note "up to 500 chars"
    timestamptz occurred_at
  }
  processed_events {
    uuid event_id PK
    timestamptz processed_at
  }
  delivery_outbox {
    uuid id PK
    uuid event_id UK
    bigint order_id
    text payload
    timestamptz published_at
  }
```

### fulfillment-service (`petplanet_fulfillment`)

```mermaid
erDiagram
  order_tracking ||--o{ order_tracking_events : "timeline (by order_id)"
  order_tracking {
    bigint order_id PK
    bigint customer_id
    varchar order_status
    timestamptz order_status_at
    numeric total_amount
    varchar payment_status
    varchar shipment_status
    varchar tracking_number
    varchar delivery_status
    boolean escalated
    varchar escalation_reason
    timestamptz created_at
    timestamptz updated_at
  }
  order_tracking_events {
    uuid event_id PK "source event id = idempotency key"
    bigint order_id
    varchar event_type
    varchar source
    text description
    timestamptz occurred_at
    timestamptz recorded_at
  }
```

Derived data only: it can be rebuilt by replaying Kafka. Indexes `(customer_id, updated_at DESC)` and a partial `(order_status, order_status_at) WHERE escalated = FALSE` for the stuck-order scan.

## Common technical tables

| Table | Where | Purpose |
|---|---|---|
| `*_outbox` | catalog, order, inventory, payment, notification, shipping, delivery | Transactional outbox. Columns: `id`, `event_id` (unique, = payload `eventId`), `topic`, `event_type`, an aggregate column (`order_id`/`pet_id`/`payment_id`), `correlation_id`, `message_key`, `payload` (JSON text), `created_at`, `published_at` (set after broker ack), `attempts`, `last_error`. Partial index on `created_at WHERE published_at IS NULL` |
| `processed_events` | inventory, payment, notification, shipping, delivery | Idempotency: `event_id` PK, `processed_at`. Written with `INSERT … ON CONFLICT DO NOTHING` in the consumer's transaction |

## Migration history

| Service | Migrations |
|---|---|
| user | `V1` users table · `V2` seed admin + customer · `V3` integer identity IDs, `active` flag, `CUSTOMER` → `USER` · `V4` fix seed password hashes (`password123`) |
| pet-catalog | `V1` pets · `V2` seed 6 pets (`11111111-1111-1111-1111-111111111101`…`106`) · `V3` `catalog_outbox` |
| order | `V1` orders + items · `V2` outbox · `V3` outbox relay columns · `V4` `PAID` → `CONFIRMED` |
| inventory | `V1` `pet_inventory` · `V2` outbox + `processed_events` · `V3` seed 6 inventory rows · `V4` outbox relay columns |
| payment | `V1` payments, outbox, `processed_events` · `V2` outbox relay columns + `correlation_id` · `V3` refunds + `closed_orders` · `V4` `customer_id` |
| notification | `V1` notifications, outbox, `processed_events` · `V2` relay columns · `V3` typed notifications (`type`, check, unique per order) |
| shipping | `V1` shipments, outbox, `processed_events` · `V2` relay columns · `V3` status mirror (check constraint; `PENDING`/`PACKAGED` → `CREATED`) |
| delivery-tracking | `V1` delivery records, outbox, `processed_events` · `V2` relay columns · `V3` `delivery_events` timeline, status check, `correlation_id`, `FAILED` → `DELIVERY_FAILED` |
| fulfillment | `V1` old `fulfillment_records` · `V2` relay columns · `V3` **drops** old tables, creates `order_tracking` + `order_tracking_events` |

## Seed and demo data

| Source | What |
|---|---|
| Flyway (`user` V2/V4) | `admin@petplanet.com` (ADMIN) and `customer@petplanet.com` (USER, "Jane"), password `password123` |
| Flyway (`catalog` V2, `inventory` V3) | 6 sample pets, all `AVAILABLE` |
| `make seed` (`scripts/seed-demo.sh`, through the public API) | 8 more pets (14 total), customer `john@petplanet.com`, 3 orders: one `DELIVERED`, one `OUT_FOR_DELIVERY` (Jane), one `IN_TRANSIT` (John). Safe to re-run: skips pets that exist by name and customers that already have orders |

Using the API for demo data keeps every service's tables and events consistent.
