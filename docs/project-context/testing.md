# Testing

## Test inventory

| Module | Test files |
|---|---|
| `pet-catalog-service` | `PetServiceTest` (creation defaults to `AVAILABLE`, archive → `RETIRED` + `PetArchived`, archiving a reserved pet rejected, update cannot change availability) |
| `order-service` | `OrderCommandServiceTest` (order creation, pricing, validation, catalog errors, cancellation), `OrderQueryServiceTest` (lookup, not-found, owner access control), `OrderSagaServiceTest` (confirm on `PetsSold`; `FAILED` + `OrderFailed` on payment failure and sale conflict; `CANCELLED` + `OrderCancelled` on expiry; late events on terminal orders ignored), `OrderStateMachineTest` (every legal/illegal transition) and `OrderControllerTest` (`@WebMvcTest` with Spring Security; malformed JSON → 400) |
| `inventory-service` | `InventoryServiceTest` (all-or-nothing reservation, duplicate `OrderCreated` ignored, unknown pet → `PET_NOT_FOUND`, sale only while still reserved → `PetsSold` or `InventorySaleConflict`, `OrderFailed` releases only this order's reservations, archived pet → `RETIRED`) |
| `payment-service` | `PaymentServiceTest` (charge the order total, declined charge, duplicate event not charged twice, closed order never charged, missing amount rejected, refund on order closure, closure without payment) with a stub `PaymentGateway` |
| `notification-service` | `NotificationServiceTest` (confirmation notification creation and duplicate event safety) |
| `shipping-service` | `ShippingServiceTest` (shipment and tracking number creation and idempotency) |
| `delivery-tracking-service` | `DeliveryTrackingServiceTest` (delivery tracking creation and idempotency) |
| `fulfillment-service` | `FulfillmentServiceTest` (initial `SHIPPED` projection creation, update to `DELIVERED` on subsequent event, and idempotency) |
| `gateway-service` | `JwtServiceTest` (RS256 token claims, validation, and tampering) and `GatewayAuthFilterTest` (identity header overwrite, admin-path and customer-scope rejection) |
| `user-service` | `UserControllerTest` (internal service-token protection and integer-ID profile lookup), `AuthServiceTest` (registration always creates a `USER`) and `UserServiceTest` (last-admin protection) |
| `platform-commons` | `OutboxRelayTest` (publish with headers, mark published only on ack, failure recorded and batch stopped) and `KafkaPlatformIntegrationTest` (**real Kafka via Testcontainers**: typed consumption without type headers, legacy `__TypeId__` header ignored, poison message routed to `<topic>.DLT` while later messages are still processed, correlation header in MDC). Skipped automatically without Docker; `src/test/resources/docker-java.properties` pins Docker API 1.44 because Docker Engine 29 rejects Testcontainers' old default. |
| 8 consuming services | `messaging/consumer/EventContractTest`: each fixture in `src/test/resources/contracts/<topic>.json` is the **exact JSON the producer wrote to Kafka**, captured from the running stack on 2026-11-14. The test asserts it deserializes into the consumer's own record with required fields present, so a renamed or retyped producer field fails the build (verified by renaming `customerId`). Covered topics: `order-created`, `order-confirmed`, `pet-reserved`, `payment-succeeded`, `inventory-status`, `shipment-created`, `delivery-status-updated`, plus (captured during the Phase 2 smoke run) `pets-sold`, `inventory-sale-conflict`, `order-failed`, `order-cancelled`. Not yet covered: `payment-failed`, `pet-reservation-failed/expired`, `pet-catalog-events`. |
| every service | `ArchitectureTest`: 11 ArchUnit package-boundary rules per service (see [Code structure](./code-structure.md)) |

Service tests are unit or slice tests with mocks. The only real-broker test is `KafkaPlatformIntegrationTest` in `platform-commons`. Per-service Testcontainers tests against Kafka and PostgreSQL are still planned (R-TEST-01 in the [restructuring plan](../restructuring/02-target-architecture-and-requirements.md)).

## End-to-end smoke test (`make smoke`)

`scripts/smoke-test.sh` runs against the running stack (R-TEST-04, part). It creates its own pets as admin and checks:
1. the happy path (`CONFIRMED`, payment `SUCCEEDED`, pet `SOLD`, shipment created);
2. archive rules (`RETIRED`, hidden, not orderable → 409; archiving a sold pet → 409; malformed order JSON → 400);
3. payment declined (`FAILED`, payment `FAILED`, pet `AVAILABLE`);
4. cancel during a slow charge (`CANCELLED`, payment `REFUNDED`, pet `AVAILABLE`);
5. reservation expiry during a slow charge (`CANCELLED`, payment `REFUNDED`, pet `AVAILABLE`).

Scenarios 3–5 restart payment/inventory with `PAYMENT_SIMULATION_RESULT`, `PAYMENT_SIMULATION_DELAY`, `INVENTORY_RESERVATION_TIMEOUT` and `INVENTORY_EXPIRATION_CHECK_DELAY_MS`, and the defaults are restored on exit. It takes about 5 minutes. The admin delivery step is not covered yet (Phase 4).

Known flake: when the full stack is running on a 4 GB Docker VM, `KafkaPlatformIntegrationTest` can fail to start its own Kafka container (exit code 126). It passes on rerun or with the stack stopped.

## Commands

- All backend services from the repository root: `./mvnw test` (Maven wrapper; no global Maven needed). One service: `./mvnw -pl order-service -am test` (`-am` also builds `platform-commons`, which services depend on).
- Frontend production build: `cd frontend && npm run build`. Frontend folder-boundary check: `npm run check:boundaries`.
