# 03 · Events and the checkout saga

All asynchronous communication goes through Apache Kafka 4.0.1. The conventions are implemented once in `platform-commons` (`com.petcommerce.platform.kafka` and `.outbox`) and switched on by Spring Boot auto-configuration in every event-driven service.

## Event lifecycle in one picture

```mermaid
flowchart LR
  subgraph Producer["Producing service"]
    A["Application service<br/>@Transactional"] -->|"1 · same transaction"| T[("business tables")]
    A -->|"1 · same transaction"| O[("*_outbox table")]
    R["OutboxRelay<br/>every 500 ms"] -->|"2 · lock rows<br/>FOR UPDATE SKIP LOCKED"| O
  end
  R -->|"3 · send JSON + headers<br/>wait for broker ack"| K[("Kafka topic")]
  R -->|"4 · set published_at"| O
  K -->|"5 · poll"| L
  subgraph Consumer["Consuming service"]
    L["@KafkaListener<br/>(messaging.consumer)"] --> S["Application service<br/>@Transactional"]
    S -->|"6 · INSERT processed_events<br/>ON CONFLICT DO NOTHING"| P[("processed_events")]
    S -->|"7 · apply change (+ own outbox rows)"| T2[("business tables")]
  end
  L -. "fails 3 times" .-> D[("topic.DLT")]
```

## Wire format

- **Values are plain JSON strings.** Producers use `StringSerializer`, consumers `StringDeserializer`. No Java class name travels with a message, so services never share event classes. Each service keeps its own copy of the payload records it needs, in `messaging/event`.
- **Typed consumption:** `platform-commons` registers a `StringJsonMessageConverter`, which converts the JSON into the `@KafkaListener` method's parameter type. Unknown JSON fields are ignored, so a producer may add fields without breaking consumers (additive evolution).
- **Headers** (written by the relay): `eventId`, `eventType`, `correlationId`. Consumers copy `correlationId` into the logging MDC, so each log line shows `[corr=<id>]`.
- **Keys:** order-scoped events are keyed by `orderId`, pet-scoped events (inventory, catalog) by `petId`. Every topic has 1 partition locally.
- **Legacy messages:** messages from before the restructuring carry a `__TypeId__` header. It is ignored, so they are still consumed.
- **Topic ownership:** each producer declares the topics it owns in its `config/KafkaConfig`. Consumers only subscribe.

## Publishing: the transactional outbox

**Problem it solves:** if a service commits its database change and then sends to Kafka, a crash in between loses the event. If it sends first, a rollback publishes an event for a change that never happened. The outbox makes the state change and the event atomic.

```mermaid
sequenceDiagram
  autonumber
  participant App as Application service
  participant DB as Service database
  participant Relay as OutboxRelay (platform-commons)
  participant K as Kafka
  App->>DB: BEGIN
  App->>DB: UPDATE business row
  App->>DB: INSERT outbox row (event_id, topic, message_key, payload)
  App->>DB: COMMIT (both or neither)
  loop every 500 ms
    Relay->>DB: BEGIN, SELECT up to 50 rows WHERE published_at IS NULL FOR UPDATE SKIP LOCKED
    Relay->>K: send(topic, key, json, headers)
    alt broker acknowledges within 10 s
      K-->>Relay: ack
      Relay->>DB: SET published_at = now()
    else send fails
      Relay->>DB: attempts + 1, last_error = message, stop this batch (keeps order)
    end
    Relay->>DB: COMMIT
  end
```

- Business code calls its `*EventPublisher` (`messaging.publisher`), which only inserts an outbox row. The outbox row's `event_id` is the payload's `eventId`.
- Each service exposes its table to the relay through an `*OutboxStore` that implements `platform-commons` `OutboxStore`. Interval: `platform.outbox.relay-interval-ms` (500 ms).
- **Delivery is at-least-once.** A crash after the send but before the commit can resend a row. That is why consumers must be idempotent.
- Outbox tables: `catalog_outbox`, `order_outbox`, `inventory_outbox`, `payment_outbox`, `notification_outbox`, `shipment_outbox`, `delivery_outbox`. fulfillment emits nothing, so it has none.

## Consuming: retries, dead-letter topics, idempotency

```mermaid
flowchart TD
  M["Message arrives"] --> P{"Parse JSON into<br/>listener type"}
  P -->|fails| RT
  P -->|ok| H["Call application service<br/>(one transaction)"]
  H --> I{"markProcessed(eventId)<br/>rows inserted?"}
  I -->|"0: already handled"| SKIP["Skip, commit offset"]
  I -->|1| W["Apply business change"]
  W -->|ok| C["Commit DB, commit offset"]
  W -->|exception| RB["Roll back everything<br/>(incl. processed_events row)"] --> RT
  RT{"Retried 3 times<br/>(1 s apart)?"} -->|no| H
  RT -->|yes| DLT["Publish to topic.DLT<br/>with exception headers,<br/>move on"]
```

- **Retries:** 3 attempts, 1 s apart (`platform.kafka.retry.*`), then the record goes to **`<topic>.DLT`** with the original value and exception headers. One bad message never blocks a partition. Unparseable JSON follows the same path.
- **Idempotency, three strategies:**
  - `processed_events` table (inventory, payment, notification, shipping, delivery-tracking): `INSERT … ON CONFLICT (event_id) DO NOTHING` inside the business transaction. `0` rows means "already handled". On failure the insert rolls back together with the business change.
  - Timeline key (fulfillment): `order_tracking_events.event_id` is the source event's ID, inserted with `ON CONFLICT DO NOTHING`.
  - State checks (order, catalog): a transition is applied only from the expected state, so a repeated event changes nothing.
- **No guessing of required fields:** an event missing a required value (for example `customerId`) fails and lands in the DLT.
- Inspect topics and DLTs with Kafka UI: `make kafka-ui` → http://localhost:8090.

## The checkout saga

### Happy path

```mermaid
sequenceDiagram
  autonumber
  participant O as order
  participant I as inventory
  participant P as payment
  participant C as pet-catalog
  participant S as shipping
  participant D as delivery-tracking
  participant N as notification
  participant F as fulfillment (projection)
  Note over O: POST /orders → order CREATED
  O-)I: OrderCreated
  O-)F: OrderCreated
  I->>I: reserve ALL pets or none (optimistic lock), expiry = now + 5 min
  I-)C: InventoryStatusChanged (RESERVED)
  I-)O: PetReserved
  I-)P: PetReserved (totalAmount, customerId)
  O->>O: CREATED → PAYMENT_PENDING
  P->>P: advisory lock on order, charge total (simulated)
  P-)I: PaymentSucceeded
  I->>I: sell only pets still RESERVED for this order
  I-)C: InventoryStatusChanged (SOLD)
  I-)O: PetsSold
  O->>O: PAYMENT_PENDING → CONFIRMED
  O-)N: OrderConfirmed
  O-)S: OrderConfirmed
  N->>N: ORDER_CONFIRMED notification
  S->>S: shipment CREATED, tracking number PET-orderId-...
  S-)D: ShipmentCreated
  S-)N: ShipmentCreated
  N->>N: SHIPPED notification
  D->>D: delivery IN_TRANSIT + timeline row
  D-)S: DeliveryStatusUpdated (IN_TRANSIT)
  S->>S: mirror: SHIPPED
  Note over D: admin PUT /deliveries/order/{id}/status
  D-)S: DeliveryStatusUpdated (OUT_FOR_DELIVERY, then DELIVERED)
  D-)N: DeliveryStatusUpdated
  S->>S: mirror: DELIVERED
  N->>N: OUT_FOR_DELIVERY, DELIVERED notifications
  Note over F: consumes every order, payment, shipment, delivery and notification event into one tracking row + timeline
```

**Key rule:** the order is confirmed **only after inventory has sold the pets**. A pet whose reservation expired can never be sold to two orders, and shipping never sees an order that could still fail.

### Compensation paths

Every unsuccessful end publishes an event. Inventory releases the order's reservations (pets still `RESERVED` for that order become `AVAILABLE`). Payment records the order in `closed_orders` and refunds a successful charge, or makes sure a later charge never happens.

| Trigger | Order becomes | Event | Inventory | Payment |
|---|---|---|---|---|
| Pet unknown / reserved / sold / retired | `FAILED` | `OrderFailed` (`RESERVATION_FAILED:<reason>`) | nothing was reserved | closed; never charges |
| Payment declined | `FAILED` | `OrderFailed` (`PAYMENT_FAILED:<reason>`) | releases | already `FAILED` |
| Customer cancels (`PATCH /orders/{id}/cancel` while `CREATED`/`PAYMENT_PENDING`) | `CANCELLED` | `OrderCancelled` (`CUSTOMER_CANCELLED`) | releases | refunds if charged, otherwise blocks the charge |
| Reservation expires (scheduler) | `CANCELLED` | `OrderCancelled` (`RESERVATION_EXPIRED`) | already released | refunds if charged, otherwise blocks |
| Payment succeeded after the reservation was lost | `FAILED` (if still open) | `InventorySaleConflict` → `OrderFailed` (`SALE_CONFLICT:<reason>`) | sells nothing | refunds |

#### Example: customer cancels while the charge is still running

```mermaid
sequenceDiagram
  autonumber
  participant B as Customer
  participant O as order
  participant I as inventory
  participant P as payment
  participant N as notification
  I-)P: PetReserved
  P->>P: take advisory lock (order 42), start slow charge
  B->>O: PATCH /orders/42/cancel
  O->>O: PAYMENT_PENDING → CANCELLED
  O-)I: OrderCancelled (CUSTOMER_CANCELLED)
  O-)P: OrderCancelled
  O-)N: OrderCancelled
  I->>I: release pet → AVAILABLE
  P->>P: charge finishes: SUCCEEDED, release lock
  P->>P: OrderCancelled waits for the lock, then closes order 42, sees SUCCEEDED, so REFUNDED
  P-)N: PaymentRefunded
  N->>N: ORDER_CANCELLED + PAYMENT_REFUNDED notifications
  Note over I: the late PaymentSucceeded finds no pet reserved for 42 → InventorySaleConflict, order already terminal so it is ignored
```

The per-order PostgreSQL **advisory lock** (`pg_advisory_xact_lock`) serializes "charge" and "close" for the same order: whichever runs second sees the other's result.

#### Example: two customers race for the same pet

```mermaid
sequenceDiagram
  autonumber
  participant A as Order A
  participant Bo as Order B
  participant I as inventory
  participant P as payment
  A-)I: OrderCreated (pet X)
  Bo-)I: OrderCreated (pet X)
  I->>I: reserve X for A (version 0 → 1) ✓
  I->>I: reserve X for B: X is RESERVED → fail
  I-)P: PetReserved (A)
  I-)Bo: PetReservationFailed (PET_ALREADY_RESERVED)
  Bo->>Bo: CREATED → FAILED, OrderFailed
  P->>P: charge A only (B is never charged)
  Note over A: A continues to CONFIRMED
```

Optimistic locking (`@Version` on `pet_inventory`) guarantees that only one reservation of a pet can commit.

## State machines

### Order (`order-service`, `domain/policy/OrderStateMachine`)

```mermaid
stateDiagram-v2
  [*] --> CREATED: POST /orders
  CREATED --> PAYMENT_PENDING: PetReserved
  PAYMENT_PENDING --> CONFIRMED: PetsSold
  CREATED --> FAILED: PetReservationFailed
  PAYMENT_PENDING --> FAILED: PaymentFailed or InventorySaleConflict
  CREATED --> CANCELLED: customer cancel
  PAYMENT_PENDING --> CANCELLED: customer cancel or PetReservationExpired
  CONFIRMED --> [*]
  FAILED --> [*]
  CANCELLED --> [*]
```

Terminal states never change. Illegal or repeated transitions are ignored, which makes every saga handler idempotent.

### Pet inventory (`inventory-service`)

```mermaid
stateDiagram-v2
  [*] --> AVAILABLE: PetCreated
  AVAILABLE --> RESERVED: OrderCreated (all or nothing)
  RESERVED --> SOLD: PaymentSucceeded (still reserved for this order)
  RESERVED --> AVAILABLE: OrderCancelled / OrderFailed / expiry
  AVAILABLE --> RETIRED: PetArchived
  SOLD --> [*]
  RETIRED --> [*]
```

### Payment (`payment-service`)

```mermaid
stateDiagram-v2
  [*] --> PROCESSING: PetReserved
  PROCESSING --> SUCCEEDED: gateway approves
  PROCESSING --> FAILED: gateway declines
  SUCCEEDED --> REFUNDED: OrderCancelled / OrderFailed
  FAILED --> [*]
  REFUNDED --> [*]
```

### Delivery (`delivery-tracking-service`) and the mirrored shipment (`shipping-service`)

```mermaid
stateDiagram-v2
  direction LR
  state "Delivery (owner)" as Del {
    [*] --> IN_TRANSIT: ShipmentCreated
    IN_TRANSIT --> OUT_FOR_DELIVERY: admin
    IN_TRANSIT --> DELIVERED: admin
    IN_TRANSIT --> DELIVERY_FAILED: admin
    OUT_FOR_DELIVERY --> DELIVERED: admin
    OUT_FOR_DELIVERY --> DELIVERY_FAILED: admin
  }
  state "Shipment (mirror)" as Shp {
    [*] --> CREATED: OrderConfirmed
    CREATED --> SHIPPED: IN_TRANSIT or OUT_FOR_DELIVERY
    SHIPPED --> S_DELIVERED: DELIVERED
    SHIPPED --> S_FAILED: DELIVERY_FAILED
  }
```

(`S_DELIVERED` / `S_FAILED` are the shipment statuses `DELIVERED` / `DELIVERY_FAILED`.) Any other delivery move returns **409**. Terminal states never change.

## Topic map

Three small diagrams, one per stage. Arrows go from producer to consumer and are labelled with the event; each event has its own topic (`OrderCreated` → `order-created-events`). **fulfillment-service consumes 12 of these topics** to build the tracking view; its arrows are left out to keep the pictures readable (the list is in the table below).

**1 · Catalog ↔ inventory sync**

```mermaid
flowchart LR
  CAT[pet-catalog] -->|"PetCreated / PetArchived"| INV[inventory]
  INV -->|"InventoryStatusChanged<br/>AVAILABLE · RESERVED · SOLD · RETIRED"| CAT
```

**2 · Checkout saga (order, inventory, payment)**

```mermaid
flowchart LR
  ORD[order]
  INV[inventory]
  PAY[payment]
  ORD -->|OrderCreated| INV
  INV -->|"PetReserved"| ORD
  INV -->|"PetReserved"| PAY
  INV -->|"PetReservationFailed<br/>PetReservationExpired"| ORD
  PAY -->|PaymentSucceeded| INV
  PAY -->|PaymentFailed| ORD
  INV -->|"PetsSold<br/>InventorySaleConflict"| ORD
  ORD -->|"OrderCancelled<br/>OrderFailed"| INV
  ORD -->|"OrderCancelled<br/>OrderFailed"| PAY
```

**3 · After the outcome (shipping, delivery, notifications)**

```mermaid
flowchart LR
  ORD[order]
  PAY[payment]
  SHP[shipping]
  DEL[delivery-tracking]
  NOT[notification]
  FUL[fulfillment]
  ORD -->|OrderConfirmed| SHP
  ORD -->|"OrderConfirmed<br/>OrderCancelled<br/>OrderFailed"| NOT
  PAY -->|PaymentRefunded| NOT
  SHP -->|ShipmentCreated| DEL
  SHP -->|ShipmentCreated| NOT
  DEL -->|DeliveryStatusUpdated| SHP
  DEL -->|DeliveryStatusUpdated| NOT
  NOT -->|NotificationSent| FUL
```

### Topic table

| Topic | Payload record | Producer | Consumers |
|---|---|---|---|
| `pet-catalog-events` | `PetCatalogEvent` (`eventType` `PetCreatedEvent` / `PetArchivedEvent`) | pet-catalog | inventory |
| `inventory-status-events` | `InventoryStatusChangedEvent` | inventory | pet-catalog |
| `order-created-events` | `OrderCreatedEvent` | order | inventory, fulfillment |
| `pet-reserved-events` | `PetReservedEvent` | inventory | order, payment, fulfillment |
| `pet-reservation-failed-events` | `PetReservationFailedEvent` | inventory | order, fulfillment |
| `pet-reservation-expired-events` | `PetReservationExpiredEvent` | inventory | order |
| `payment-succeeded-events` | `PaymentSucceededEvent` | payment | inventory, fulfillment |
| `payment-failed-events` | `PaymentFailedEvent` | payment | order, fulfillment |
| `payment-refunded-events` | `PaymentRefundedEvent` | payment | notification, fulfillment |
| `pets-sold-events` | `PetsSoldEvent` | inventory | order |
| `inventory-sale-conflict-events` | `InventorySaleConflictEvent` | inventory | order |
| `order-confirmed-events` | `OrderConfirmedEvent` | order | notification, shipping, fulfillment |
| `order-cancelled-events` | `OrderCancelledEvent` | order | inventory, payment, notification, fulfillment |
| `order-failed-events` | `OrderFailedEvent` | order | inventory, payment, notification, fulfillment |
| `shipment-created-events` | `ShipmentCreatedEvent` | shipping | delivery-tracking, notification, fulfillment |
| `delivery-status-updated-events` | `DeliveryStatusUpdatedEvent` | delivery-tracking | shipping (mirror), notification, fulfillment |
| `notification-sent-events` | `NotificationSentEvent` | notification | fulfillment (timeline) |
| `fulfillment-updated-events` | — | retired | — |
| `pet-reservation-released-events` | — | retired (releases are announced on `inventory-status-events`) | — |

The two retired topics are no longer produced or declared, but still exist in an old local Kafka volume with their old messages until removed manually.

### Payloads

Most events share these envelope fields: `eventId` (UUID), `eventType`, `eventVersion`, `aggregateId` (UUID), `timestamp` (ISO-8601 instant), `correlationId` (UUID). A fuller envelope (`causationId`, `producer`, `aggregateId` = order ID) is **not implemented**; `aggregateId` is often a random UUID, and the two pet-scoped events have no `correlationId`.

| Record | Fields beyond the envelope |
|---|---|
| `OrderCreatedEvent` | `Long orderId`, `Long customerId`, `List<UUID> petIds`, `BigDecimal totalAmount` |
| `OrderConfirmedEvent` | `orderId`, `customerId`, `petIds`, `totalAmount` |
| `OrderCancelledEvent` | `orderId`, `customerId`, `petIds`, `String reason` (`CUSTOMER_CANCELLED` / `RESERVATION_EXPIRED`) |
| `OrderFailedEvent` | `orderId`, `customerId`, `petIds`, `String reason` (`RESERVATION_FAILED:…` / `PAYMENT_FAILED:…` / `SALE_CONFLICT:…`) |
| `PetReservedEvent` | `orderId`, `UUID reservationId`, `UUID petId`, `BigDecimal totalAmount`, `Long customerId` |
| `PetReservationFailedEvent` | `orderId`, `petId`, `String reason` (`PET_NOT_FOUND`, `PET_ALREADY_RESERVED`, `PET_ALREADY_SOLD`, `PET_RETIRED`) |
| `PetReservationExpiredEvent` | `orderId`, `petId` |
| `PetsSoldEvent` | `orderId`, `List<UUID> petIds` |
| `InventorySaleConflictEvent` | `orderId`, `String reason` |
| `InventoryStatusChangedEvent` | `UUID petId`, `String status`, `Long orderId`, `Instant timestamp` (no `aggregateId`/`correlationId`) |
| `PetCatalogEvent` | `UUID petId`, `String availabilityStatus`, `Instant timestamp` (no `aggregateId`/`correlationId`) |
| `PaymentSucceededEvent` | `orderId`, `UUID paymentId`, `BigDecimal amount`, `Long customerId` |
| `PaymentFailedEvent` | `orderId`, `paymentId`, `amount`, `String reason`, `customerId` |
| `PaymentRefundedEvent` | `orderId`, `paymentId`, `amount`, `String reason`, `customerId` |
| `ShipmentCreatedEvent` | `orderId`, `customerId`, `UUID shipmentId`, `String trackingNumber`, `String status` |
| `DeliveryStatusUpdatedEvent` | `orderId`, `customerId`, `UUID deliveryId`, `trackingNumber`, `status`, `previousStatus` (null for the first `IN_TRANSIT`), `note` |
| `NotificationSentEvent` | `orderId`, `UUID notificationId`, `String message`, `customerId`, `String notificationType` |

`customerId` on payment events is `null` for payments created before payment migration `V4` (old local data only).

### Contract safety

Each consuming service has an `EventContractTest` with fixtures in `src/test/resources/contracts/<topic>.json`: the **exact JSON a producer wrote to Kafka**, captured from the running stack. A renamed or retyped producer field fails the consumer's build. Topics without fixtures: `pet-reservation-expired-events`, `pet-catalog-events`. See [11 Testing](11-testing.md).
