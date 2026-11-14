# Testing

## Test inventory

| Module | Test files |
|---|---|
| `pet-catalog-service` | `PetServiceTest` (creation defaults to `AVAILABLE`, archive → `RETIRED` + `PetArchived`, archiving a reserved pet rejected, update cannot change availability) |
| `order-service` | `OrderCommandServiceTest` (order creation, pricing, validation, catalog errors, cancellation), `OrderQueryServiceTest` (lookup, not-found, owner access control), `OrderSagaServiceTest` (confirm on `PetsSold`; `FAILED` + `OrderFailed` on payment failure and sale conflict; `CANCELLED` + `OrderCancelled` on expiry; late events on terminal orders ignored), `OrderStateMachineTest` (every legal/illegal transition) and `OrderControllerTest` (`@WebMvcTest` with Spring Security; malformed JSON → 400) |
| `inventory-service` | `InventoryServiceTest` (all-or-nothing reservation, duplicate `OrderCreated` ignored, unknown pet → `PET_NOT_FOUND`, sale only while still reserved → `PetsSold` or `InventorySaleConflict`, `OrderFailed` releases only this order's reservations, archived pet → `RETIRED`) |
| `payment-service` | `PaymentServiceTest` (charge the order total, declined charge, duplicate event not charged twice, closed order never charged, missing amount rejected, refund on order closure, closure without payment) with a stub `PaymentGateway` |
| payment, notification, shipping, delivery-tracking, fulfillment | `*OwnershipTest` (owner allowed, foreign customer → `AccessDeniedException`, admin allowed, record without owner admin-only, missing → `*NotFoundException`, customer lists self/admin only) and `*ControllerTest` (`@WebMvcTest` with **real RS256 tokens** from `TestJwtIssuer`: 401 without or with a forged token, token subject passed to the ownership check, 403/404 shared error body, `…/delivered` admin-only) |
| `notification-service` | `NotificationServiceTest` (confirmation notification creation and duplicate event safety) |
| `shipping-service` | `ShippingServiceTest` (shipment and tracking number creation and idempotency) |
| `delivery-tracking-service` | `DeliveryTrackingServiceTest` (delivery tracking creation and idempotency) |
| `fulfillment-service` | `FulfillmentServiceTest` (initial `SHIPPED` projection creation, update to `DELIVERED` on subsequent event, and idempotency) |
| `gateway-service` | `GatewaySecurityTest` (`@SpringBootTest` on a random port with real tokens and real routes to a stub HTTP backend that echoes received headers: 401 shared body, forged/expired tokens, JWT forwarded and untrusted headers stripped, no ownership callout, internal token only on user-service routes, admin-only prefixes, `/internal/**` denied, CORS headers on 401) and `CorsConfigTest` |
| `user-service` | `UserControllerTest` (internal service-token protection; `/me` uses the token's user ID and ignores identity headers; 401 without token; admin-only user administration), `TokenIssuerTest` (issued tokens pass the platform validator with `sub`/`email`/`role`; other key rejected), `AuthServiceTest` (registration always creates a `USER`) and `UserServiceTest` (last-admin protection) |
| `platform-commons` | `PlatformSecurityTest` (shared resource-server setup end to end: owner/foreign/admin via `AuthenticatedCaller`, role rule, 401 for missing/forged/expired/wrong-issuer/wrong-audience/non-numeric-subject/unknown-role tokens, shared `ApiError` body with `correlationId`), `OutboxRelayTest` (publish with headers, mark published only on ack, failure recorded and batch stopped) and `KafkaPlatformIntegrationTest` (**real Kafka via Testcontainers**: typed consumption without type headers, legacy `__TypeId__` header ignored, poison message routed to `<topic>.DLT` while later messages are still processed, correlation header in MDC). Skipped automatically without Docker; `src/test/resources/docker-java.properties` pins Docker API 1.44 because Docker Engine 29 rejects Testcontainers' old default. |
| 8 consuming services | `messaging/consumer/EventContractTest`: each fixture in `src/test/resources/contracts/<topic>.json` is the **exact JSON the producer wrote to Kafka**, captured from the running stack on 2026-11-14. The test asserts it deserializes into the consumer's own record with required fields present, so a renamed or retyped producer field fails the build (verified by renaming `customerId`). Covered topics: `order-created`, `order-confirmed`, `pet-reserved`, `payment-succeeded`, `inventory-status`, `shipment-created`, `delivery-status-updated`, plus (captured during the Phase 2 smoke run) `pets-sold`, `inventory-sale-conflict`, `order-failed`, `order-cancelled`. Not yet covered: `payment-failed`, `pet-reservation-failed/expired`, `pet-catalog-events`. |
| every service | `ArchitectureTest`: 11 ArchUnit package-boundary rules per service (see [Code structure](./code-structure.md)) |

`order-service` `OrderControllerTest` additionally checks a real signed token, 401 and the shared 404 body. `TestJwtIssuer` (throwaway RSA key, same claims as user-service) ships in the `platform-commons` **test-jar**, which services use as a test dependency.

Service tests are unit or slice tests with mocks. The only real-broker test is `KafkaPlatformIntegrationTest` in `platform-commons`. Per-service Testcontainers tests against Kafka and PostgreSQL are still planned (R-TEST-01 in the [restructuring plan](../restructuring/02-target-architecture-and-requirements.md)).

## End-to-end smoke test (`make smoke`)

`scripts/smoke-test.sh` runs against the running stack (R-TEST-04, part). It creates its own pets as admin and checks:
1. the happy path (`CONFIRMED`, payment `SUCCEEDED`, pet `SOLD`, shipment created);
2. archive rules (`RETIRED`, hidden, not orderable → 409; archiving a sold pet → 409; malformed order JSON → 400);
3. payment declined (`FAILED`, payment `FAILED`, pet `AVAILABLE`);
4. cancel during a slow charge (`CANCELLED`, payment `REFUNDED`, pet `AVAILABLE`);
5. reservation expiry during a slow charge (`CANCELLED`, payment `REFUNDED`, pet `AVAILABLE`);

plus scenario **S (security, Phase 3)**: a freshly registered second customer gets 403 on the first customer's order, payment, shipment, notification, delivery and fulfillment records and notification list, while the owner and the admin get 200; 401 without a token with the shared error body; a service 403 with the shared body; user on `/users` and on `…/delivered` → 403; unknown order payment → 404; `/users/me` works from the token.

Scenarios 3–5 restart payment/inventory with `PAYMENT_SIMULATION_RESULT`, `PAYMENT_SIMULATION_DELAY`, `INVENTORY_RESERVATION_TIMEOUT` and `INVENTORY_EXPIRATION_CHECK_DELAY_MS`, and the defaults are restored on exit. It takes about 5 minutes. The admin delivery step is not covered yet (Phase 4).

Known flake: when the full stack is running on a 4 GB Docker VM, `KafkaPlatformIntegrationTest` can fail to start its own Kafka container (exit code 126). It passes on rerun or with the stack stopped.

## Commands

- All backend services from the repository root: `./mvnw test` (Maven wrapper; no global Maven needed). One service: `./mvnw -pl order-service -am test` (`-am` also builds `platform-commons`, which services depend on).
- Frontend production build: `cd frontend && npm run build`. Frontend folder-boundary check: `npm run check:boundaries`.
