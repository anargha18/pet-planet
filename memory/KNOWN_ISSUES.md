# Known issues (open as of 2026-11-14, Phase 3 done)

IDs refer to `docs/restructuring/01-audit-findings.md`. Fixed so far: F1, F2, F3, F4 (via one-time reset), F5, F6, S1, S2, S3, S4, S5, S6, S7, S8, S14, U1 (backend side), A1–A5 (Phase 3, verified by tests + `make smoke` 49/49). Remove an entry only after the fix is verified.

## Open — planned phase

| Issue | Phase |
|---|---|
| S9 Two "delivered" endpoints (shipping's emits no event; delivery's doesn't update shipment) | 4 |
| S10 shipping/delivery/fulfillment store overlapping state; fulfillment not yet a tracking projection | 4 |
| S11 Topics with no consumer: `notification-sent`, `fulfillment-updated`, `pet-reservation-released`, `payment-refunded` | 4 |
| S12 Notification: one per order, says "on the way" at confirmation; no notice for cancel/fail/refund/delivery | 4 |
| U2 No admin UI; U3 no 401/expired-token handling or protected routes; U4 tracking page swallows errors, no polling; U5 duplicate axios instances | 5 |
| R-EVT-02 full envelope (`causationId`, `producer`, `aggregateId`=order) not implemented; `aggregateId` often random | later |
| R-EVT-08: HTTP correlation ID is in the MDC/error bodies, but order-service does not reuse it as the saga `correlationId` | later |
| R-TEST-01 per-service Testcontainers (Kafka+Postgres) tests not written; contract fixtures missing for `payment-failed`, `pet-reservation-failed/expired`, `pet-catalog-events` | 6 |
| Smoke test has no admin "mark delivered" step | 4 |
| Old dev RSA key pair still in git history (removed from tree; rotate local `.env` with `generate-dev-keys.sh --force`) | accepted |
| Payments created before payment `V4` have `customer_id` NULL → admin-only | accepted |
| README saga step 4 still says `PAID` (stale since Phase 2) | 6 |

## Environment / tooling gotchas (confirmed)

- Services need `JWT_PUBLIC_KEY` at startup (no default). For IDE runs export it from `.env`. `@WebMvcTest`s get it via `TestJwtIssuer` (platform-commons test-jar) + `@DynamicPropertySource`.
- `@AutoConfigureWebTestClient` with RANDOM_PORT gives relative URIs → CORS "origin is malformed"; use the auto-provided real-port `WebTestClient`.
- One unexplained 688 s run of payment `ArchitectureTest` in a full build (0.8 s on rerun).

- `KafkaPlatformIntegrationTest` (Testcontainers) can fail to start its Kafka (exit 126) when the full stack runs on the 4 GB Docker VM — passes on rerun.
- Testcontainers 1.21 needs `platform-commons/src/test/resources/docker-java.properties` (`api.version=1.44`) for Docker Engine 29.
- Single-module builds need `-am`: `./mvnw -pl <service> -am test` (depends on `platform-commons`).
- Shell is **zsh**: unquoted `$VAR` holding a command or file list is NOT word-split (caused failed `docker exec`/`git add` calls). Use functions, arrays, or `--pathspec-from-file`.
- Docker Desktop has ~4 GB; full stack ~3.1 GB. 6 GB recommended.
- Maven Central downloads can be slow/drop; Dockerfiles use a shared BuildKit cache (`id=pet-planet-m2`).
- Helper scripts used during restructuring lived in the session scratchpad and are **not** in the repo (they are not needed to continue).
