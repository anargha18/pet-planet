# Implementation status

**As of 2026-11-15, after restructuring Phase 6.** "Verified" means a named, repeatable check demonstrates the behavior: a test in `./mvnw verify`, a scenario in `make smoke`, or a check in `make ui-check`. Anything else is labelled. The history of how the system got here is in the [restructuring plan's progress log](../restructuring/02-target-architecture-and-requirements.md#progress-log); the defects found on 2026-11-13 are in the [audit](../restructuring/01-audit-findings.md).

## Features

| Feature | Status | Evidence |
|---|---|---|
| One-command local stack | Verified | `make up` builds and starts 13 containers, all healthy. A fresh clone with no `.env` and new volumes started healthy and passed `make smoke` on 2026-11-15 (Phase 6). |
| Identity and tokens (user-service) | Verified | BCrypt credentials, register/login, RS256 JWTs signed only by user-service (`sub` = user ID). Self-service profile/password; admin user CRUD with soft deactivation and a last-admin guard. Tests: `TokenIssuerTest`, `UserControllerTest`, seed-hash test; smoke: both seeded logins, registration. UI: profile page, admin users page. |
| Gateway | Verified | Validates tokens, applies coarse role rules, CORS (including on 401/403), never routes `/internal/**`, adds `X-Correlation-Id`. Test: `GatewaySecurityTest`; smoke scenario S. |
| Service-level security and ownership | Verified | Every service validates the JWT itself (shared platform-commons setup). Order, payment, notification, shipping, delivery-tracking and fulfillment return 403 for another customer's records; admins see all. One shared error body. Tests: per-service `*OwnershipTest`/`*ControllerTest`, `PlatformSecurityTest`; smoke scenario S. |
| Pet catalog | Verified | Public browse/search, admin create/edit/archive (`RETIRED`; 409 for reserved or sold pets). Availability is a read-only copy synced from inventory. Tests: `PetServiceTest`; smoke scenario 2; UI: admin pets page. |
| Checkout saga | Verified | `CREATED → PAYMENT_PENDING → CONFIRMED`, with `CANCELLED`/`FAILED` terminal and enforced in one place (`OrderStateMachine`). An order is confirmed only after inventory sold the pets. Smoke covers: happy path (1); payment declined (3); cancel during a slow charge (4); reservation expiry during a slow charge (5); two customers racing for one pet, where exactly one wins and the other gets `PetReservationFailed` and is never charged (R). |
| Inventory | Verified | All-or-nothing reservation, sale only while still reserved for that order (otherwise `InventorySaleConflict`), release on cancel/failure, configurable expiry, read-only admin API (single pet and paged overview). Tests: `InventoryServiceTest`, `InventoryControllerTest`; smoke scenarios 1–5 and R. |
| Payment (simulated, DEC-4) | Verified | Charges the order total automatically, refunds a successful charge when the order is cancelled or failed, and never charges a closed order (per-order advisory lock). Tests: `PaymentServiceTest`; smoke scenarios 3–5. There is no real payment provider. |
| Shipping and delivery | Verified | Shipping creates shipments and mirrors delivery status (read-only API). Delivery-tracking owns the one admin action `PUT /api/v1/deliveries/order/{id}/status` (`IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED / DELIVERY_FAILED`; illegal moves 409) and a timeline. Tests: `DeliveryStatusTest`, `DeliveryTrackingServiceTest`, `ShippingServiceTest`; smoke scenarios 1 and D; UI: admin deliveries page. |
| Notifications | Verified | One typed notification per order and type (8 types) with reason-specific wording; lists per order, customer and `/me`. Sending is simulated: stored and marked `SENT`, with no e-mail. Tests: `NotificationServiceTest`; smoke scenarios 1, 3, 4, 5, D, R. |
| Order-tracking projection (fulfillment) | Verified | CQRS view from 12 topics with a timeline. Idempotent via the timeline's event IDs, row-locked per order, monotonic under any event order. Escalates on delivery failure and on orders stuck before confirmation, and clears the stuck flag when the order closes. Tests: `OrderTrackingTest`, `OrderTrackingProjectorTest`, `EscalationServiceTest`, and `OrderTrackingProjectionPostgresTest` (real PostgreSQL: out-of-order events, replay, 5 concurrent listeners on one new order); smoke scenarios 1, D and 6 (live stuck-order escalation and clearing). |
| Event backbone | Verified | Transactional outbox in 7 producers with a shared relay (publish after broker ack), plain-JSON contracts, retries then `<topic>.DLT`, idempotent consumers. Tests: `OutboxRelayTest`, `KafkaPlatformIntegrationTest` (real Kafka: legacy messages, DLT), consumer `EventContractTest`s from captured producer JSON. |
| Storefront | Verified | One API client with token/401 handling (expired session → `/login?expired=1`), protected and admin routes, role-aware navigation, profile, orders and tracking with live polling after checkout, admin pets/inventory/orders/users/deliveries. Check: `make ui-check` (29 checks) and the Postman collection for the same API flows. |
| Code structure | Verified | Per-service package layout enforced by 11 ArchUnit rules per service; frontend feature boundaries by `npm run check:boundaries`. |

## Known limitations

These are deliberate scope limits or open items, not hidden defects. Owners and details are in [`memory/KNOWN_ISSUES.md`](../../memory/KNOWN_ISSUES.md).

- **Learning-project scope:** payment and notifications are simulated. There is no production deployment, TLS between services, rate limiting or secrets manager. The internal gateway token only protects user-service inside Compose.
- **Events:** the full envelope of R-EVT-02 (`causationId`, `producer`, `aggregateId` = order) is not implemented. order-service starts a new saga `correlationId` instead of reusing the HTTP request's correlation ID.
- **Tests:** only `platform-commons` (Kafka) and fulfillment (PostgreSQL) have Testcontainers tests. The other services' consumer→producer hops are covered by unit tests, contract fixtures and the smoke test (R-TEST-01 partly done). There are no contract fixtures for `pet-reservation-expired-events` and `pet-catalog-events`. The frontend has no unit tests, only the `make ui-check` walkthrough.
- **History:** the pre-restructuring development RSA key is still in git history (rotated out of the tree). Payments created before payment `V4` have no owner and are admin-only. Retired topics `fulfillment-updated-events` and `pet-reservation-released-events` still exist in Kafka with their old messages.
- **UI:** the admin deliveries page filters a 50-row page of the tracking view to shipped orders. No favicon.

## Re-verifying

`make verify` (backend, needs Docker), then with the stack up: `make smoke`, `make ui-check`. Last results are recorded in [Testing](./testing.md).
