# Pet Planet — audit findings (2026-11-13)

Audit of branch `config-analysed` **including uncommitted work** (gateway-owned auth refactor). No code was changed during the audit. Each finding has an ID that [02-target-architecture-and-requirements.md](./02-target-architecture-and-requirements.md) refers to.

Verification legend:
- **Verified**: reproduced or proven during the audit (command/output noted).
- **Code-confirmed**: follows directly from reading the code and framework behavior; not exercised at runtime because the Docker daemon was not running and Maven is not installed locally.
- **Risk**: a likely problem depending on the machine or environment.

---

## 1. Why the project does not run (blockers)

| ID | Problem | Evidence | Status |
|---|---|---|---|
| **F1** | **Seeded accounts cannot log in.** The BCrypt hash in the seed matches neither `password123` (the README value) nor `password`. | [`user-service/.../V2__seed_users.sql`](../../user-service/src/main/resources/db/migration/V2__seed_users.sql). Checked with Spring's `BCrypt.checkpw` against `password123`, `password`, `admin` and `admin123`: all `false`. | Verified |
| **F2** | **Browser login/registration is blocked by CORS.** `spring.cloud.gateway.globalcors` applies only to *routed* requests, plus the simple URL handler mapping. `/api/v1/auth/login` and `/register` are served by a `@RestController` (gateway `AuthController`, removed in Phase 0b when token issuance moved to user-service) through `RequestMappingHandlerMapping`, which has no CORS config, so the preflight `OPTIONS` gets no `Access-Control-Allow-Origin`. Catalog browsing works because it is routed. Login from `:5173` fails. | [`gateway-service/.../application.yml`](../../gateway-service/src/main/resources/application.yml). The `FRONTEND_ORIGIN` env var is passed by Compose but never read. | Code-confirmed |
| **F3** | **No cross-service Kafka event can be deserialized, so the saga never moves past `CREATED`.** Every producer uses `JsonSerializer`, which adds a `__TypeId__` header holding the *producer's* class name (e.g. `com.petcommerce.order.event.OrderCreatedEvent`). Every consumer uses `JsonDeserializer` with type headers enabled, so it tries to load that class. The class doesn't exist in the consumer (which has e.g. `com.petcommerce.inventory.event.OrderCreatedEvent`), deserialization fails, and the record is skipped or retried and lost. The outbox *pollers* send `JsonNode` objects instead of records, which produces yet another type header. There is no `ErrorHandlingDeserializer` and no dead-letter topic, so failures are silent. | All 10 `application.yml` Kafka blocks (identical config, no `spring.json.use.type.headers` / `value.default.type` / `add.type.headers`). Relay code, e.g. `InventoryService.relayOutboxEvents()` → `objectMapper.readTree(...)`. | Code-confirmed |
| **F4** | **Existing local databases crash-loop on startup.** Commit `a68b1ce` edited already-created Flyway `V1`/`V2` migrations in 9 services. Any Postgres volume created from `104b7ef` fails Flyway checksum validation. The README mentions `docker compose down -v` only for an order-identity change. The Postgres init script also only runs on a fresh volume, so a volume created before a database was added never gets that database. | `git log --name-status -- '*/db/migration/*'` | Verified |
| **F5** | **Services can't be built or run outside Docker.** There is no Maven wrapper (`mvnw`) in any service, no root aggregator POM, and Maven isn't installed on this machine. Running one service from the IDE against containerized infra is undocumented. | `find . -name mvnw` → none; `which mvn` → not found | Verified |
| **F6** | **Heavy, racy startup.** 10 JVMs, Kafka, Postgres and a Vite dev server start with no memory limits or JVM flags. Spring services have no healthchecks, and `depends_on` uses `service_started`, so services start before their dependencies are ready. | [`docker-compose.yml`](../../docker-compose.yml) | Risk |
| **F7** | The Docker daemon was not running at audit time (`docker info` printed only client info). | — | Environment |

## 2. Saga and domain correctness

| ID | Problem | Evidence |
|---|---|---|
| **S1** | **Double sale possible.** On `OrderConfirmedEvent`, inventory calls `markSold(petId)` without checking that the pet is still `RESERVED` *for that order*. After an expiry, another order may hold the pet. | `InventoryService.handleOrderConfirmedEvent` / `markSold` |
| **S2** | **Money taken, no pet.** If the reservation expires or the customer cancels while payment is in flight, `order-service` ignores the later `PaymentSucceededEvent` ("unexpected state"). Nothing refunds the payment. Expiry also does **not** emit `OrderCancelledEvent`, so other reserved pets in the same order are released only by their own timers, and no one tells payment. | `OrderService.handlePaymentSucceeded`, `handlePetReservationExpired` |
| **S3** | `PAID` and `CONFIRMED` are both order statuses, but `CONFIRMED` is never set. The frontend has to handle both. | `OrderStatus`, `OrderService` |
| **S4** | Inventory "reserves" pets it has never heard of: `orElseGet(() -> new PetInventory(petId))` creates an `AVAILABLE` row, so the `PET_NOT_FOUND` branch can never be reached. | `InventoryService.handleOrderCreatedEvent`, `reservePet` |
| **S5** | **Two sources of truth for availability.** Admin `PUT /api/v1/pets/{id}` overwrites `availabilityStatus` directly, so catalog and inventory diverge. `DELETE` is a hard delete with no event, leaving orphan inventory rows and order snapshots pointing at nothing. `PetCreatedEvent` is sent with `kafkaTemplate.send` *inside* the DB transaction rather than via an outbox: it is lost if Kafka is down, and published even if the transaction rolls back. | `PetService.update/delete/create` |
| **S6** | A hard-coded fallback `customerId = 101L` is spread across services: secondary constructors in `OrderConfirmedEvent` (order, inventory) and `OrderCreatedEvent` (inventory), plus notification, shipping, delivery and fulfillment. It can silently attach records to a fake customer. | `grep -rn 101L` |
| **S7** | **The outbox is not reliable.** Two publish paths (an `afterCommit` async send plus a `@Scheduled` poller) race and produce duplicates. The poller marks a row `published_at` right after an *async* `send()` without waiting for the broker ack, so failures are marked published. The `afterCommit` callback saves a detached entity from the Kafka producer thread. The same code is copy-pasted 7×. | Every `*Service.relayOutboxEvents()` and `sendAndMarkPublished()` |
| **S8** | The idempotency check catches `DataIntegrityViolationException` *inside* the same `@Transactional` method. Once that exception is thrown, the transaction is marked rollback-only, so the "ignore duplicate" path ends in `UnexpectedRollbackException`. | inventory, payment, notification, shipping, delivery, fulfillment services |
| **S9** | **Two "delivered" endpoints that disagree.** `PUT /shipments/order/{id}/delivered` updates only the shipment and emits **no** event. `PUT /deliveries/order/{id}/delivered` emits an event but never updates the shipment. | `ShippingService.markDelivered`, `DeliveryTrackingService.markDelivered` |
| **S10** | **Overlapping responsibilities.** shipping (`SHIPPED/DELIVERED`), delivery-tracking (`IN_TRANSIT/DELIVERED`) and fulfillment (`SHIPPED/DELIVERED/ESCALATED`) store nearly the same state three times. Fulfillment has no distinct job. | entities + services |
| **S11** | Topics nobody consumes: `notification-sent-events`, `fulfillment-updated-events`, `pet-reservation-released-events`. | consumers grep |
| **S12** | Notification supports exactly one notification per order (`findByOrderId` returns `Optional`), and on confirmation it says the order "is on the way". Cancelled or failed orders and deliveries produce no notification. | `NotificationService` |
| **S13** | Payment is charged automatically the moment pets are reserved. That is fine as a simulation, but the "cancel while `PAYMENT_PENDING`" window is effectively zero, and the docs don't say payment is simulated. | `PaymentService` |
| **S14** | Admin inventory endpoints (`/reserve`, `/release`, `/sold`) change state outside the saga, with no order link and no compensation. | `InventoryController` |

## 3. Security and API consistency

| ID | Problem | Evidence |
|---|---|---|
| **A1** | **Inconsistent "defense in depth".** A hand-rolled `JwtService` is copied into gateway, order, catalog and inventory, while payment, notification, shipping, delivery and fulfillment have **no** security. The principal is the email in catalog but the userId in order. | `*/security/JwtService.java`, `pom.xml` (starter-security present in 4 of 10) |
| **A2** | **Chatty, coupled ownership checks.** For every customer GET on payments, notifications, shipments, deliveries or fulfillment, the gateway synchronously calls `order-service` to check ownership, even though each service already stores `customerId`. | `GatewayAuthFilter.forwardIfOrderOwned` |
| **A3** | Controllers return JPA entities (`Payment`, `Shipment`, `Notification`, `DeliveryRecord`, `FulfillmentRecord`). This leaks internals (`version`, ids) and ties the API to the schema. | the five `*Controller.java` |
| **A4** | Error responses differ by service (`{error}`, `ApiError`, Spring default). `IllegalArgumentException` in shipping/delivery `markDelivered` becomes a 500. | exception handlers |
| **A5** | The RSA **private key is committed** in `.env.example`, and `.env` uses the identical pair. The public key is also hard-coded as a default in three `application.yml` files. | `.env.example`, catalog/order/inventory `application.yml` |
| **A6** | CORS is configured in three places with different rules: gateway YAML, `CorsConfigurationSource` beans in catalog/order/inventory (some disabled), and nowhere for `/auth`. | SecurityConfig classes |

## 4. Frontend ↔ backend coordination

| ID | Problem | Evidence |
|---|---|---|
| **U1** | Nobody can log in from the browser (F1 + F2). Every authenticated feature downstream is unreachable. | — |
| **U2** | No admin UI exists, although the backend has pet CRUD, inventory, user management and "mark delivered". The stored `role` is never used. | `frontend/src/App.jsx` routes |
| **U3** | No 401 or expired-token handling. A stale token in `localStorage` keeps sending failing requests. There are no protected routes. | `services/api.js`, `AuthContext.jsx` |
| **U4** | Tracking makes three separate calls (payment, shipment, notification), swallows all errors, ignores delivery and fulfillment, and never polls. Users can't watch the saga progress. | `pages/OrdersPage.jsx` |
| **U5** | Three identical axios instances. Checkout calls `getCurrentUser` and discards the result. | `services/api.js`, `CheckoutPage.jsx` |
| **U6** | There is a stray root `node_modules/` with no root `package.json`. The frontend container runs the Vite *dev* server. | repo root, `frontend/Dockerfile` |

## 5. Documentation drift

| ID | Problem |
|---|---|
| **D1** | [`implementation-status.md`](../project-context/implementation-status.md) marks every feature "Implemented", which F1–F3, S1, S2, S5, S7 and S9 contradict. This breaks the AGENTS.md rule "never document partial functionality as complete". |
| **D2** | [`architecture.md`](../project-context/architecture.md) describes a working end-to-end delivered flow and a reliable outbox. Neither holds (S7, S9). |
| **D3** | [`infrastructure/postgres/init/README.md`](../../infrastructure/postgres/init/README.md) still says initialization "will be added". [`inventory-service/README.md`](../../inventory-service/README.md) lists only one consumed topic. |
| **D4** | `postman/` contains only empty globals. There is no collection or smoke script. |

## 6. Testing gap (why tests can be green while the system is broken)

Every existing test is a mock-based unit test. No test uses a real Kafka broker, real JSON (de)serialization across services, a real Postgres/Flyway, or a gateway CORS preflight. F2 and F3 are therefore invisible to the suite. The tests were **not run** during this audit (no Maven available).

## 7. What is in good shape and should be kept

- Database-per-service with dedicated Postgres roles. The init script works on a fresh volume.
- Flyway on every service, with `ddl-auto: validate`.
- Order item snapshots (price, name, breed) taken at checkout.
- One topic per event type, which makes the F3 fix simple.
- Outbox and `processed_events` tables already exist in every producing service (the relay logic is what needs fixing).
- user-service domain logic: integer IDs, soft deactivation, last-admin protection, email normalization.
- Optimistic locking on inventory.
- The React storefront's catalog browsing, species filter, pagination and cart.
