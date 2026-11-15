# Event catalog and Kafka conventions

All asynchronous communication goes through Kafka 4.0.1. The conventions below are implemented once in [`platform-commons`](../../platform-commons) and used by every event-driven service (catalog, order, inventory, payment, notification, shipping, delivery-tracking, fulfillment; fulfillment only consumes). The topic and payload tables were generated from the code (`KafkaConfig`, `@KafkaListener` methods, `messaging/event` records).

## Wire format (R-EVT-01)

- **Values are plain JSON strings.** Producers use `StringSerializer` and consumers `StringDeserializer` (each service's `application.yml`). No Java class name travels with a message, so services never share event classes. Each service keeps its own copy of the payload records in `messaging/event`.
- **Typed consumption:** `platform-commons` registers a `StringJsonMessageConverter` that converts the JSON into the `@KafkaListener` method's parameter type. Unknown JSON fields are ignored, so a producer may add fields without breaking consumers.
- **Headers** (written by the outbox relay): `eventId`, `eventType`, `correlationId`. Consumers copy them into the logging MDC, and every log line shows `[corr=<correlationId>]`.
- **Keys:** order-scoped events are keyed by `orderId`; inventory and catalog events by `petId`. All topics currently have 1 partition.
- **Legacy messages:** messages written before Phase 1 carry a `__TypeId__` header naming the producer's class. It is ignored, so they are still consumed (verified by `KafkaPlatformIntegrationTest`).

## Publishing: transactional outbox (R-EVT-03)

1. Business code calls its service's `*EventPublisher` inside the business `@Transactional` method. The publisher only **inserts an outbox row** (event JSON, topic, message key, `event_id` taken from the payload), so the row commits or rolls back together with the state change.
2. `platform-commons` `OutboxRelay` runs every 500 ms (`platform.outbox.relay-interval-ms`) for each service's `OutboxStore`. In one transaction it:
   - locks up to 50 unpublished rows (`SELECT … FOR UPDATE SKIP LOCKED`);
   - sends each one and **waits for the broker acknowledgement** (timeout 10 s);
   - sets `published_at`.
3. On failure the row keeps `published_at = NULL`, `attempts` is incremented and `last_error` is stored. The batch stops to preserve order, and the next run retries.

Delivery is **at-least-once**: a crash after the send but before the commit can resend a row. Consumers are therefore idempotent.

Outbox tables (one per producing service): `catalog_outbox`, `order_outbox`, `inventory_outbox`, `payment_outbox`, `notification_outbox`, `shipment_outbox`, `delivery_outbox`. (`fulfillment_outbox` was dropped in Phase 4: the tracking projection emits nothing.)

## Consuming: retries, dead-letter topics, idempotency (R-EVT-04, R-EVT-05)

- A listener failure is retried 3 times, 1 s apart (`platform.kafka.retry.*`). The record is then published to **`<topic>.DLT`** with the original value and exception headers, and the consumer moves on, so one bad message no longer blocks a partition. Unreadable JSON goes to the DLT after the same retries.
- Services with a `processed_events` table (inventory, payment, notification, shipping, delivery-tracking) call `ProcessedEventRepository.markProcessed(eventId)`: an `INSERT … ON CONFLICT DO NOTHING` inside the business transaction. A result of `0` means the event was already handled and is skipped. If processing fails, the insert rolls back with everything else.
- fulfillment deduplicates with its timeline: `order_tracking_events.event_id` is the source event's ID, inserted with `ON CONFLICT DO NOTHING`; `0` rows means already projected.
- order-service and catalog-service rely on state checks (a transition is only applied from the expected state) instead of a `processed_events` table.
- Required fields are not guessed. For example, an event without `customerId` fails and ends up in the DLT; the former `101L` fallback was removed (R-EVT-07).
- Inspect DLTs and messages with Kafka UI: `make kafka-ui`, then http://localhost:8090.

## Topics

Each producer declares the topics it owns in its `config/KafkaConfig` (R-EVT-06). Consumers only subscribe. Phase 2 added `order-failed-events`, `pets-sold-events`, `inventory-sale-conflict-events` and `payment-refunded-events`. `OrderCancelledEvent` gained a `reason` (`CUSTOMER_CANCELLED` / `RESERVATION_EXPIRED`). The sale is completed by inventory on `payment-succeeded-events`, and the order is confirmed on `pets-sold-events` (see [Architecture](./architecture.md#1-happy-path-checkout-saga)). Phase 4 gave every remaining topic a consumer (S11): notification and fulfillment now consume the saga outcome topics, shipping mirrors `delivery-status-updated-events`, fulfillment (consumer group `fulfillment-tracking-projection`) consumes 12 topics, and `fulfillment-updated-events` / `pet-reservation-released-events` are **retired** (no longer produced or declared; the topics and their old messages stay in Kafka until removed manually).

| Topic | Payload record | Producer | Consumers |
|---|---|---|---|
| `delivery-status-updated-events` | `DeliveryStatusUpdatedEvent` | delivery-tracking | shipping (mirror), notification, fulfillment |
| `fulfillment-updated-events` | — | — (retired in Phase 4) | — |
| `inventory-sale-conflict-events` | `InventorySaleConflictEvent` | inventory | order |
| `inventory-status-events` | `InventoryStatusChangedEvent` | inventory | pet-catalog |
| `notification-sent-events` | `NotificationSentEvent` | notification | fulfillment (timeline) |
| `order-cancelled-events` | `OrderCancelledEvent` | order | inventory, payment, notification, fulfillment |
| `order-confirmed-events` | `OrderConfirmedEvent` | order | notification, shipping, fulfillment |
| `order-created-events` | `OrderCreatedEvent` | order | inventory, fulfillment |
| `order-failed-events` | `OrderFailedEvent` | order | inventory, payment, notification, fulfillment |
| `payment-failed-events` | `PaymentFailedEvent` | payment | order, fulfillment |
| `payment-refunded-events` | `PaymentRefundedEvent` | payment | notification, fulfillment |
| `payment-succeeded-events` | `PaymentSucceededEvent` | payment | inventory, fulfillment |
| `pet-catalog-events` | `PetCatalogEvent` (`eventType` `PetCreatedEvent` / `PetArchivedEvent`) | pet-catalog | inventory |
| `pet-reservation-expired-events` | `PetReservationExpiredEvent` | inventory | order |
| `pet-reservation-failed-events` | `PetReservationFailedEvent` | inventory | order, fulfillment |
| `pet-reservation-released-events` | — | — (retired in Phase 4; releases are announced on `inventory-status-events`) | — |
| `pet-reserved-events` | `PetReservedEvent` | inventory | order, payment, fulfillment |
| `pets-sold-events` | `PetsSoldEvent` | inventory | order |
| `shipment-created-events` | `ShipmentCreatedEvent` | shipping | delivery-tracking, notification, fulfillment |

## Payloads

Most events share these envelope fields: `eventId`, `eventType`, `eventVersion`, `aggregateId`, `timestamp` (ISO-8601 instant), `correlationId`. The full standard envelope in R-EVT-02 (`causationId`, `producer`, `aggregateType`, `aggregateId` = order ID) is **not implemented yet**. Today `aggregateId` is often a random UUID, and `InventoryStatusChangedEvent` / `PetCatalogEvent` have no `correlationId`.

| Record | Fields (JSON property = name) |
|---|---|
| `DeliveryStatusUpdatedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `Long customerId`, `UUID deliveryId`, `String trackingNumber`, `String status`, `String previousStatus`, `String note` (both added in Phase 4; `previousStatus` is null for the first `IN_TRANSIT` event) |
| `InventorySaleConflictEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `String reason` |
| `InventoryStatusChangedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID petId`, `String status`, `Long orderId`, `Instant timestamp` |
| `NotificationSentEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `UUID notificationId`, `String message`, `Long customerId`, `String notificationType` (both added in Phase 4) |
| `OrderCancelledEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `Long customerId`, `List<UUID> petIds`, `String reason` |
| `OrderConfirmedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `Long customerId`, `List<UUID> petIds`, `BigDecimal totalAmount` |
| `OrderCreatedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `Long customerId`, `List<UUID> petIds`, `BigDecimal totalAmount` |
| `OrderFailedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `Long customerId`, `List<UUID> petIds`, `String reason` |
| `PaymentFailedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `UUID paymentId`, `BigDecimal amount`, `String reason`, `Long customerId` (added in Phase 4; null for payments created before payment `V4`) |
| `PaymentRefundedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `UUID paymentId`, `BigDecimal amount`, `String reason`, `Long customerId` (added in Phase 4; null for payments created before payment `V4`) |
| `PaymentSucceededEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `UUID paymentId`, `BigDecimal amount`, `Long customerId` (added in Phase 4; null for payments created before payment `V4`) |
| `PetCatalogEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID petId`, `String availabilityStatus`, `Instant timestamp` |
| `PetReservationExpiredEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `UUID petId` |
| `PetReservationFailedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `UUID petId`, `String reason` |
| `PetReservedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `UUID reservationId`, `UUID petId`, `BigDecimal totalAmount`, `Long customerId` (added in Phase 3 so payment can enforce ownership; additive, so older messages without it still deserialize, as `null`) |
| `PetsSoldEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `List<UUID> petIds` |
| `ShipmentCreatedEvent` | `UUID eventId`, `String eventType`, `Integer eventVersion`, `UUID aggregateId`, `Instant timestamp`, `UUID correlationId`, `Long orderId`, `Long customerId`, `UUID shipmentId`, `String trackingNumber`, `String status` |
