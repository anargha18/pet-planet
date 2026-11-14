# Testing

## Test inventory

| Module | Test files |
|---|---|
| `pet-catalog-service` | `PetServiceTest` (verifies pet creation defaults to `AVAILABLE`) |
| `order-service` | `OrderCommandServiceTest` (order creation, pricing, validation, catalog errors, cancellation), `OrderQueryServiceTest` (lookup, not-found, owner access control), `OrderSagaServiceTest` (payment success/failure handling) and `OrderControllerTest` (`@WebMvcTest` with Spring Security) |
| `inventory-service` | `InventoryServiceTest` (reservation, optimistic locking conflict, idempotent `OrderCreatedEvent` & `OrderConfirmedEvent` handling) |
| `payment-service` | `PaymentServiceTest` (simulated payment success/failure, idempotency, outbox persistence) |
| `notification-service` | `NotificationServiceTest` (confirmation notification creation and duplicate event safety) |
| `shipping-service` | `ShippingServiceTest` (shipment and tracking number creation and idempotency) |
| `delivery-tracking-service` | `DeliveryTrackingServiceTest` (delivery tracking creation and idempotency) |
| `fulfillment-service` | `FulfillmentServiceTest` (initial `SHIPPED` projection creation, update to `DELIVERED` on subsequent event, and idempotency) |
| `gateway-service` | `JwtServiceTest` (RS256 token claims, validation, and tampering) and `GatewayAuthFilterTest` (identity header overwrite, admin-path and customer-scope rejection) |
| `user-service` | `UserControllerTest` (internal service-token protection and integer-ID profile lookup), `AuthServiceTest` (registration always creates a `USER`) and `UserServiceTest` (last-admin protection) |
| `platform-commons` | `OutboxRelayTest` (publish with headers, mark published only on ack, failure recorded and batch stopped) and `KafkaPlatformIntegrationTest` (**real Kafka via Testcontainers**: typed consumption without type headers, legacy `__TypeId__` header ignored, poison message routed to `<topic>.DLT` while later messages are still processed, correlation header in MDC). Skipped automatically without Docker; `src/test/resources/docker-java.properties` pins Docker API 1.44 because Docker Engine 29 rejects Testcontainers' old default. |
| 8 consuming services | `messaging/consumer/EventContractTest`: each fixture in `src/test/resources/contracts/<topic>.json` is the **exact JSON the producer wrote to Kafka**, captured from the running stack on 2026-11-14. The test asserts it deserializes into the consumer's own record with required fields present, so a renamed or retyped producer field fails the build (verified by renaming `customerId`). Covered topics: `order-created`, `order-confirmed`, `pet-reserved`, `payment-succeeded`, `inventory-status`, `shipment-created`, `delivery-status-updated`. Not yet captured because those flows haven't produced traffic: `payment-failed`, `pet-reservation-failed/expired`, `order-cancelled`, `pet-catalog-events`. |
| every service | `ArchitectureTest`: 11 ArchUnit package-boundary rules per service (see [Code structure](./code-structure.md)) |

Service tests are unit or slice tests with mocks. The only real-broker test is `KafkaPlatformIntegrationTest` in `platform-commons`. Per-service Testcontainers tests against Kafka and PostgreSQL are still planned (R-TEST-01 in the [restructuring plan](../restructuring/02-target-architecture-and-requirements.md)).

## Commands

- All backend services from the repository root: `./mvnw test` (Maven wrapper; no global Maven needed). One service: `./mvnw -pl order-service -am test` (`-am` also builds `platform-commons`, which services depend on).
- Frontend production build: `cd frontend && npm run build`. Frontend folder-boundary check: `npm run check:boundaries`.
