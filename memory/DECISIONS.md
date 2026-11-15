# Decisions

Confirmed decisions only. Full context: `docs/restructuring/02-target-architecture-and-requirements.md` §1 and the per-phase "Deviations" notes.

## Owner-confirmed (2026-11-14)

| ID | Decision | Why |
|---|---|---|
| DEC-1 | **user-service issues RS256 JWTs** (only holder of the private key); **gateway only validates** (public key), routes, applies coarse role rules. | Edge holds no business logic or secrets. |
| DEC-2 | **fulfillment-service becomes the order-tracking read model** (CQRS projection). Built in Phase 4. | Keep every service but give each a distinct job. |
| DEC-3 | **`platform-commons`** shared module: infrastructure only (Kafka conventions, outbox relay). No domain/event classes. | Removes 7× copy-paste without coupling domains. |
| DEC-4 | Payment stays **simulated and automatic** on reservation, but compensatable (refunds). | Learning project; real payment step later. |
| DEC-5 | WIP committed as checkpoint before restructuring (`da52467`). | Reviewable diffs. |
| DEC-6 | **All 10 services stay at the repo root.** Restructuring is *inside* each service (`api`/`application`/`domain`/`security`/`messaging`/`persistence`/`client`/`config`) and a **feature-based frontend**, enforced by ArchUnit + `check:boundaries`. | Owner explicitly rejected a `services/` folder. |
| — | Keep all existing capabilities; standard, explainable patterns; it must run with one command. | Owner goals. |
| — | Work proceeds phase by phase; owner says "yes" before each phase. | Owner preference. |

## Deliberate deviations made during implementation (recorded in the plan)

- **Application services return response DTOs** (mapped via `api.mapper` inside the transaction) — lazy `Order.items` with open-in-view off. ArchUnit rule adjusted accordingly.
- **ArchUnit runs as plain JUnit `@Test`** (core `archunit` artifact): the `archunit-junit5` engine was not discovered by Surefire (0 tests ran).
- **TokenIssuer uses JJWT** (moved verbatim from gateway) rather than Spring `JwtEncoder`, to keep the token format identical; revisit with R-SEC-02.
- **Kafka wire format = plain JSON strings** + `StringJsonMessageConverter` inferring the listener type, instead of `ErrorHandlingDeserializer` + per-listener default types. No type headers, no shared classes.
- **DLT name explicitly `<topic>.DLT`** (library default differs by version).
- **Saga flow (Phase 2): inventory completes the sale on `PaymentSucceeded` (only if still reserved) and order confirms on `PetsSold`.** The plan originally confirmed on payment and failed confirmed orders on conflict; this keeps CONFIRMED terminal and shipping only sees truly sold orders.
- **Payment closes the charge/cancel race with a per-order PostgreSQL advisory lock** + `closed_orders` marker.
- **Catalog archive = status `RETIRED`** (no separate `archived` column).
- Outbox `event_id` = payload `eventId`; payment fallback `1000.00` and customer fallback `101L` removed (missing required fields → DLT).
- Migration immutability: never edit an applied Flyway migration (one-time `make reset` was required because earlier commits did).

## Phase 3 deviations / decisions (2026-11-14, made during implementation, not separately owner-confirmed)

- **Shared security + error shape live in `platform-commons`** (`security`, `web` packages; optional deps, conditional auto-config). Extends DEC-3's scope beyond Kafka/outbox, still infrastructure only. Why: avoids 9 copies of the same resource-server config, one claim mapping by construction. Gateway reuses `PlatformJwt` (its servlet/Kafka auto-configs stay off).
- **Token `sub` = user ID** (was email); `email` is a claim. TokenIssuer now uses Nimbus `JwtEncoder` (JJWT removed everywhere).
- **Foreign records → 403** (not 404), consistent with order-service; reveals order-ID existence, accepted.
- **payment `V4` `customer_id`** filled from new additive `PetReservedEvent.customerId`; pre-V4 payments are admin-only.
- **inventory unknown pet → 404** (was a made-up AVAILABLE row).
- **Gateway CORS moved into the security chain** so 401/403 carry CORS headers.
- **Keys:** `generate-dev-keys.sh` keeps existing values unless `--force` (so `make up` never invalidates tokens).

## Phase 4 deviations / decisions (2026-11-15, made during implementation, not separately owner-confirmed)

- **Notification types `OUT_FOR_DELIVERY` and `DELIVERY_FAILED`** were added beyond R-SAGA-09's six; `IN_TRANSIT` sends nothing (`SHIPPED` on ShipmentCreated covers it).
- **Delivery may skip `OUT_FOR_DELIVERY`** (`IN_TRANSIT → DELIVERED/DELIVERY_FAILED` allowed); terminal statuses never change.
- **Fulfillment projection rebuilt by replay**: new consumer group `fulfillment-tracking-projection` + `earliest`; `V3` drops the old tables (derived data). Idempotency = timeline `event_id` (no `processed_events`). Row lock per order; status updates monotonic.
- **Fulfillment emits no events**; `fulfillment-updated-events` and `pet-reservation-released-events` retired (topics left in Kafka).
- **Payment outcome events carry `customerId`** (additive) so notification/fulfillment know the owner; notification falls back to an earlier notification's owner before dead-lettering.
- `GET /notifications/order/{id}` returns a list (`[]` when none), not 404.
- Stuck-order escalation covers `CREATED` and `PAYMENT_PENDING` and is cleared when the order closes.

## Phase 5 deviations / decisions (2026-11-15, not separately owner-confirmed)

- **New `GET /api/v1/inventory/pets?status=`** (paged, admin, read-only) for the admin inventory page instead of one request per pet; `InventoryResponse` + `orderId`, `updatedAt`.
- **Admin deliveries page uses the tracking projection** (no "all deliveries" endpoint added); orders without a shipment are filtered out client-side.
- **API functions take no token**; the shared client injects it. `AuthProvider` sits inside `BrowserRouter` so it can redirect on 401.
- Optional nginx production stage (R-FE-06) not built.

## Phase 6 decisions (2026-11-15, not separately owner-confirmed)

- **Gateway DNS cache capped at 5 s** (`gateway.upstream-dns-cache-ttl`) instead of honouring Docker's long TTLs.
- **Hikari pool 5 per service** via the Compose `x-java-env` anchor (`DB_POOL_SIZE`), rather than raising Postgres `max_connections`.
- **UI walkthrough committed** (`frontend/scripts/ui-check.mjs`, `make ui-check`) with `puppeteer-core` 23.11.1 as a pinned dev dependency (uses local Chrome; no browser download).
- **Smoke scenarios R (race) and 6 (stuck order)** added; fulfillment escalation knobs exposed in Compose.
- R-TEST-01 done only for the projection (Postgres) and platform Kafka; other per-service Testcontainers tests left open.

