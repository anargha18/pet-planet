# Testing

## Test inventory

| Module | Test files |
|---|---|
| `pet-catalog-service` | `PetServiceTest` (creation defaults to `AVAILABLE`, archive → `RETIRED` + `PetArchived`, archiving a reserved pet rejected, update cannot change availability) |
| `order-service` | `OrderCommandServiceTest` (order creation, pricing, validation, catalog errors, cancellation), `OrderQueryServiceTest` (lookup, not-found, owner access control), `OrderSagaServiceTest` (confirm on `PetsSold`; `FAILED` + `OrderFailed` on payment failure and sale conflict; `CANCELLED` + `OrderCancelled` on expiry; late events on terminal orders ignored), `OrderStateMachineTest` (every legal/illegal transition) and `OrderControllerTest` (`@WebMvcTest` with Spring Security; malformed JSON → 400) |
| `inventory-service` | `InventoryServiceTest` (all-or-nothing reservation, duplicate `OrderCreated` ignored, unknown pet → `PET_NOT_FOUND`, sale only while still reserved → `PetsSold` or `InventorySaleConflict`, `OrderFailed` releases only this order's reservations, archived pet → `RETIRED`), `InventoryControllerTest` (admin paged overview with status filter, user 403) |
| `payment-service` | `PaymentServiceTest` (charge the order total, declined charge, duplicate event not charged twice, closed order never charged, missing amount rejected, refund on order closure, closure without payment) with a stub `PaymentGateway` |
| payment, notification, shipping, delivery-tracking, fulfillment | `*OwnershipTest` (owner allowed, foreign customer → `AccessDeniedException`, admin allowed, record without owner admin-only, missing → `*NotFoundException`, customer lists self/admin only; notification: empty list for an order without notifications; fulfillment: `OrderTrackingQueryServiceTest`, search admin-only) and `*ControllerTest` (`@WebMvcTest` with **real RS256 tokens** from `TestJwtIssuer`: 401 without or with a forged token, token subject passed to the ownership check, 403/404 shared error body; delivery status change admin-only with 404/409/400; shipping has no mutation endpoint) |
| `notification-service` | `NotificationServiceTest` (one typed notification per event: wording per type and reason, refund owner taken from an earlier notification or dead-lettered, `IN_TRANSIT` sends nothing, duplicate event / existing type sends nothing, `NotificationSentEvent` payload) |
| `shipping-service` | `ShippingServiceTest` (shipment created as `CREATED`, idempotency, delivery status mirrored `SHIPPED → DELIVERED`, terminal shipment never changes, unknown shipment skipped) |
| `delivery-tracking-service` | `DeliveryStatusTest` (transition table, `deliveredAt`), `DeliveryTrackingServiceTest` (start `IN_TRANSIT` with timeline + event and saga correlation ID, idempotency, admin advance publishes `previousStatus`/`note`, illegal transition has no side effects, unknown order 404) |
| `order-service` (client) | `PetCatalogClientTest` (local JDK HTTP server: available pet, sold → unavailable, 404 → not found, 500/400 → catalog unavailable, read timeout, unreachable) |
| `fulfillment-service` | `OrderTrackingTest` (monotonic order/payment/shipment/delivery status under any event order, refund wins, delivery failure escalates, stuck escalation cleared on close), `OrderTrackingProjectorTest` (row ensured + timeline + change, replayed event changes nothing), `EscalationServiceTest` (stuck order escalated with timeline row), `OrderTrackingProjectionPostgresTest` (**Testcontainers, real PostgreSQL 16 + Flyway**: out-of-order events, replay idempotency, 5 concurrent listeners on one new order, escalation query) |
| `gateway-service` | `GatewaySecurityTest` (`@SpringBootTest` on a random port with real tokens and real routes to a stub HTTP backend that echoes received headers: 401 shared body, forged/expired tokens, JWT forwarded and untrusted headers stripped, no ownership callout, internal token only on user-service routes, admin-only prefixes, `/internal/**` denied, CORS headers on 401) and `CorsConfigTest` |
| `user-service` | `UserControllerTest` (internal service-token protection; `/me` uses the token's user ID and ignores identity headers; 401 without token; admin-only user administration), `TokenIssuerTest` (issued tokens pass the platform validator with `sub`/`email`/`role`; other key rejected), `AuthServiceTest` (registration always creates a `USER`) and `UserServiceTest` (last-admin protection) |
| `platform-commons` | `PlatformSecurityTest` (shared resource-server setup end to end: owner/foreign/admin via `AuthenticatedCaller`, role rule, 401 for missing/forged/expired/wrong-issuer/wrong-audience/non-numeric-subject/unknown-role tokens, shared `ApiError` body with `correlationId`), `OutboxRelayTest` (publish with headers, mark published only on ack, failure recorded and batch stopped) and `KafkaPlatformIntegrationTest` (**real Kafka via Testcontainers**: typed consumption without type headers, legacy `__TypeId__` header ignored, poison message routed to `<topic>.DLT` while later messages are still processed, correlation header in MDC). Skipped automatically without Docker; `src/test/resources/docker-java.properties` pins Docker API 1.44 because Docker Engine 29 rejects Testcontainers' old default. |
| 8 consuming services | `messaging/consumer/EventContractTest`: each fixture in `src/test/resources/contracts/<topic>.json` is the **exact JSON the producer wrote to Kafka**, captured from the running stack on 2026-11-14. The test asserts it deserializes into the consumer's own record with required fields present, so a renamed or retyped producer field fails the build (verified by renaming `customerId`). Covered topics: `order-created`, `order-confirmed`, `pet-reserved`, `payment-succeeded`, `inventory-status`, `shipment-created`, `delivery-status-updated`, plus (captured during the Phase 2 smoke run) `pets-sold`, `inventory-sale-conflict`, `order-failed`, `order-cancelled`. Phase 4 added `payment-failed`, `payment-refunded`, `notification-sent` and the new-shape `delivery-status-updated` (with `previousStatus`/`note`), captured from the Phase 4 smoke run on 2026-11-15, plus copies of existing fixtures for the new consumers (notification, shipping, fulfillment). Phase 6 added `pet-reservation-failed` (order, fulfillment), captured from the smoke race scenario. Not yet covered: `pet-reservation-expired`, `pet-catalog-events`. |
| every service | `ArchitectureTest`: 11 ArchUnit package-boundary rules per service (see [Code structure](./code-structure.md)) |

`order-service` `OrderControllerTest` additionally checks a real signed token, 401 and the shared 404 body. `TestJwtIssuer` (throwaway RSA key, same claims as user-service) ships in the `platform-commons` **test-jar**, which services use as a test dependency.

Most service tests are unit or slice tests with mocks. Real infrastructure via Testcontainers: `KafkaPlatformIntegrationTest` (platform-commons, real Kafka) and `OrderTrackingProjectionPostgresTest` (fulfillment, real PostgreSQL). Per-service Testcontainers tests of every consumer→producer hop (R-TEST-01) are not written; those hops are covered by unit tests, contract fixtures and `make smoke`.

## End-to-end smoke test (`make smoke`)

`scripts/smoke-test.sh` runs against the running stack (R-TEST-04, part). It creates its own pets as admin and checks:
1. the happy path (`CONFIRMED`, payment `SUCCEEDED`, pet `SOLD`, shipment created), then the **admin delivery steps** (Phase 4): delivery starts `IN_TRANSIT` and shipment mirrors `SHIPPED`; admin sets `OUT_FOR_DELIVERY` then `DELIVERED` (200), going back is 409; the delivery timeline lists all three; the shipment mirrors `DELIVERED`; shipping has no mutation endpoint (403); notifications are `ORDER_CONFIRMED,SHIPPED,OUT_FOR_DELIVERY,DELIVERED`; the tracking view shows `CONFIRMED SUCCEEDED DELIVERED DELIVERED`, not escalated, with every step in its timeline;

   **D. delivery fails** (Phase 4): admin sets `DELIVERY_FAILED` → shipment `DELIVERY_FAILED`, tracking escalated (`DELIVERY_FAILED`), `DELIVERY_FAILED` notification, admin escalated-order search finds it, a customer cannot search (403) but lists their own;
2. archive rules (`RETIRED`, hidden, not orderable → 409; archiving a sold pet → 409; malformed order JSON → 400);
3. payment declined (`FAILED`, payment `FAILED`, pet `AVAILABLE`, `ORDER_FAILED` notification, tracking `FAILED FAILED`);
4. cancel during a slow charge (`CANCELLED`, payment `REFUNDED`, pet `AVAILABLE`, `ORDER_CANCELLED` + `PAYMENT_REFUNDED` notifications, tracking `CANCELLED REFUNDED`);
5. reservation expiry during a slow charge (`CANCELLED`, payment `REFUNDED`, pet `AVAILABLE`, tracking `CANCELLED REFUNDED`, expiry wording in the cancel notification);
6. an order stuck before confirmation (Phase 6): with `FULFILLMENT_STUCK_AFTER=PT10S` and a 35 s charge, tracking escalates the `PAYMENT_PENDING` order (`STUCK_BEFORE_CONFIRMATION`), then clears the flag once it is confirmed;

and scenario **R (reservation race, Phase 6)**: two customers order the same new pet at the same moment; exactly one order is `CONFIRMED`, the other `FAILED` with an "no longer available" notification, a `PetReservationFailed` timeline entry and no payment (404);

plus scenario **S (security, Phase 3)**: a freshly registered second customer gets 403 on the first customer's order, payment, shipment, notification, delivery and fulfillment records and notification list, while the owner and the admin get 200; 401 without a token with the shared error body; a service 403 with the shared body; user on `/users` and on the delivery status change → 403; unknown order payment → 404; `/users/me` works from the token.

Scenarios 3–6 restart payment, inventory and fulfillment with `PAYMENT_SIMULATION_RESULT`, `PAYMENT_SIMULATION_DELAY`, `INVENTORY_RESERVATION_TIMEOUT`, `INVENTORY_EXPIRATION_CHECK_DELAY_MS`, `FULFILLMENT_STUCK_AFTER` and `FULFILLMENT_ESCALATION_CHECK_MS`, and the defaults are restored on exit. It takes about 3 minutes on a fresh database and up to 7 on a long-running one. 

`KafkaPlatformIntegrationTest` used to fail intermittently (exit code 126) when the Docker VM was busy. Cause: Testcontainers 1.21 runs the broker's starter script as soon as the file exists, before Docker has finished copying it. The test now waits until the script is executable and non-empty (Phase 6); it passed 3/3 runs with the full stack up and in every `make verify` since.

## UI walkthrough (`make ui-check`)

`frontend/scripts/ui-check.mjs` drives a locally installed Chrome/Chromium headlessly (`puppeteer-core`, a dev dependency; no browser download; `CHROME_PATH` if Chrome is not in a standard location) through every customer and admin page against the running stack: 29 checks covering guest redirects, login with return to the requested page, role-aware navigation, "Admins only", profile, add to cart → checkout → live order page until `Confirmed` with shipment and notification, a forged token → `/login?expired=1`, an expired token dropped on load, every admin page, and an admin "Delivered" action reflected on the order page. It fails on any JavaScript console error. It logs in through the real form and continues in a fresh tab, because headless Chrome's native password prompt swallows later input in the tab where a password was typed. There are no frontend unit tests; `npm run build` and `npm run check:boundaries` are the static checks.

## Postman (`postman/`)

`postman/collections/pet-planet-smoke.postman_collection.json` (with `postman/environments/local.postman_environment.json`) mirrors the smoke happy path through the gateway: sign in as both accounts, create a pet, check out, poll until `CONFIRMED`, check the simulated payment, wait for the delivery, show that a customer cannot advance it (403, shared error body), advance it as admin (timeline), 409 for going back, shipment mirroring, the tracking view with its timeline, typed notifications and the admin views (R-TEST-05). Run it in order in Postman or with `newman run … -e …`.

## Commands

- All backend services from the repository root: `./mvnw test` (Maven wrapper; no global Maven needed). One service: `./mvnw -pl order-service -am test` (`-am` also builds `platform-commons`, which services depend on).
- Frontend production build: `cd frontend && npm run build`. Frontend folder-boundary check: `npm run check:boundaries`.

## Latest results (2026-11-15, end of Phase 6)

| Check | Result |
|---|---|
| `./mvnw verify` (all 11 modules) | BUILD SUCCESS, 337 tests |
| `make smoke` on the long-running stack | 82/82 |
| Clean checkout (no `.env`, fresh volumes) → `make up` | 13 containers healthy in about 90 s (warm Maven cache) |
| `make smoke` on the clean checkout | 82/82 |
| `make ui-check` | 29/29 on the long-running stack; 29/29 twice on the clean checkout |
| Postman collection (`newman run`) | 23 requests, 22/22 assertions |
| `npm run build`, `npm run check:boundaries` | OK |

