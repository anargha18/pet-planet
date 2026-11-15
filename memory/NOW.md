# NOW — restructuring checkpoint

**Updated:** 2026-11-15 · **Branch:** `config-analysed` (nothing pushed) · **HEAD:** end of Phase 5 (`c593f99`) + Phase 6 changes in the working tree
**Working tree at checkpoint:** Phase 6 implemented, verified and documented; **not yet committed**.

## Where we are

Restructuring per `docs/restructuring/02-target-architecture-and-requirements.md` (requirement IDs `R-…`, phase plan in §4, detailed per-phase log in "Progress log").

| Phase | Status | Verified by |
|---|---|---|
| 0a Baseline + internal structure | **Done** | `./mvnw test` same 49 functional tests; ArchUnit; `docker compose build` |
| 0b Make it run | **Done** | Live: browser-equivalent login/CORS checks, all containers healthy |
| 1 Event backbone | **Done** | Live order → CONFIRMED path; poison message → `.DLT`; real-Kafka test |
| 2 Saga correctness | **Done** | `make smoke` 22/22 on 2026-11-14 |
| 3 Security consolidation | **Done** | `make smoke` 49/49 on 2026-11-14 |
| 4 Downstream boundaries | **Done** | `./mvnw test` 329 tests green; `make smoke` 74/74 on 2026-11-15 |
| 5 Frontend alignment | **Done** | build + boundaries; headless UI walkthrough 29/29; `make smoke` 74/74 (2026-11-15) |
| 6 Verify + document | **Done (uncommitted)** | `./mvnw verify` 337 tests; `make smoke` 82/82 (long-running + clean checkout); `make ui-check` 29/29; Postman 22/22 |

Last full verification (2026-11-15): `./mvnw test` BUILD SUCCESS, all 11 modules (329 tests); `npm run check:boundaries` + `npm run build` OK; `make up` all healthy with existing data (new migrations: delivery V3, shipping V3, notification V3, fulfillment V3); fulfillment projection replay matched order-service (16 orders); `make smoke` → 74 passed, 0 failed.

## Phase 4 in one paragraph

One admin delivery action (`PUT /api/v1/deliveries/order/{id}/status`, timeline, 409 on illegal moves); shipping mirrors delivery status (read-only API); typed notifications (8 types, one per order+type, lists per order/customer/me); fulfillment = order-tracking projection (12 topics, `order_tracking` + `order_tracking_events`, escalation, `/api/v1/fulfillment/orders/...`); retired `fulfillment-updated-events` + `pet-reservation-released-events`; payment outcome events + `customerId`; order R-SVC-04 (overloads removed, catalog client timeouts/mapping); smoke + docs updated. Details: plan "Progress log" → Phase 4; decisions in DECISIONS.md.

## Commits by phase (oldest → newest)

- Checkpoint/docs: `da52467`, `761a7f9`, `d941ecb`
- 0a: `1369362` … `694c11d` · 0b: `0162ea5` … `24ac5b2` · 1: `59f672e` … `8ac25a9` · 2: `b0f791a` … `499990a` · 3: `22def82` … `3a3e028`
- 4: `3e02395` … `69f6fab`
- 5: commits after `69f6fab` up to the Phase 5 docs commit

## Phase 5 in one paragraph

One axios client (token + 401 interceptors, expired session → `/login?expired=1`), `ProtectedRoute`/`AdminRoute`, role-aware nav, profile + password, orders list from `/fulfillment/orders/me`, order page with timeline/notifications/2 s polling, simulated-payment notices, admin pets/inventory/orders/users/deliveries. Backend: `GET /api/v1/inventory/pets` (+ `orderId`, `updatedAt`). Details: plan "Progress log" → Phase 5; frontend.md.

## Phase 6 in one paragraph

Verify green (Kafka Testcontainers race fixed); smoke scenarios R (race) + 6 (live stuck escalation) → 82 checks; fulfillment Postgres Testcontainers test; `pet-reservation-failed` fixture; `make ui-check` + `make verify` targets; Postman collection; README + implementation-status rewritten. Found and fixed: gateway stale-DNS 500s after restarts, Postgres connection headroom (pool 5). Definition of done verified on a clean checkout. Details: plan "Progress log" → Phase 6.

## Next action

1. Owner reviews Phase 6; on request commit (test fixes / gateway+compose fixes / smoke+ui-check+postman / docs+memory).
2. Restructuring complete after that. Remaining open items are in KNOWN_ISSUES.md ("open") — only on owner request.
