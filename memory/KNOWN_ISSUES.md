# Known issues (open as of 2026-11-15, restructuring complete)

IDs refer to `docs/restructuring/01-audit-findings.md`. Fixed so far: F1, F2, F3, F4 (via one-time reset), F5, F6, S1, S2, S3, S4, S5, S6, S7, S8, S14, U1 (backend side), A1–A5 (Phase 3, verified by tests + `make smoke` 49/49), S9–S12 (Phase 4, verified by tests + `make smoke` 74/74), U2–U5 (Phase 5, verified by a 29-check headless UI walkthrough). Remove an entry only after the fix is verified.

## Open — planned phase

| Issue | Phase |
|---|---|
| R-EVT-02 full envelope (`causationId`, `producer`, `aggregateId`=order) not implemented; `aggregateId` often random | open |
| R-EVT-08: HTTP correlation ID is in the MDC/error bodies, but order-service does not reuse it as the saga `correlationId` | open |
| R-TEST-01 only partly done: Testcontainers in platform-commons (Kafka) and fulfillment (Postgres); other hops via unit/contract/smoke. No fixtures for `pet-reservation-expired`, `pet-catalog-events` | open |
| Retired topics `fulfillment-updated-events`, `pet-reservation-released-events` still exist in Kafka with old messages | accepted |
| No frontend unit tests (only `make ui-check` walkthrough) | open |
| Admin deliveries page filters a 50-row tracking page client-side to shipped orders | accepted |
| Old dev RSA key pair still in git history (removed from tree; rotate local `.env` with `generate-dev-keys.sh --force`) | accepted |
| Payments created before payment `V4` have `customer_id` NULL → admin-only | accepted |

## Environment / tooling gotchas (confirmed)

- Services need `JWT_PUBLIC_KEY` at startup (no default). For IDE runs export it from `.env`. `@WebMvcTest`s get it via `TestJwtIssuer` (platform-commons test-jar) + `@DynamicPropertySource`.
- `@AutoConfigureWebTestClient` with RANDOM_PORT gives relative URIs → CORS "origin is malformed"; use the auto-provided real-port `WebTestClient`.
- One unexplained 688 s run of payment `ArchitectureTest` in a full build (0.8 s on rerun).

- `KafkaPlatformIntegrationTest` exit-126 flake FIXED in Phase 6 (Testcontainers starter-script race; test waits for an executable script).
- Testcontainers 1.21 needs `platform-commons/src/test/resources/docker-java.properties` (`api.version=1.44`) for Docker Engine 29.
- Single-module builds need `-am`: `./mvnw -pl <service> -am test` (depends on `platform-commons`).
- Shell is **zsh**: unquoted `$VAR` holding a command or file list is NOT word-split (caused failed `docker exec`/`git add` calls). Use functions, arrays, or `--pathspec-from-file`.
- Docker Desktop has ~4 GB; full stack ~3.1 GB. 6 GB recommended.
- Maven Central downloads can be slow/drop; Dockerfiles use a shared BuildKit cache (`id=pet-planet-m2`).
- Helper scripts used during restructuring lived in the session scratchpad and are **not** in the repo (they are not needed to continue).
- Spring needs `@Autowired` on one constructor when a bean has two (fulfillment `EscalationService` failed to start; unit tests did not catch it — only `make up` did).
- Postgres superuser in compose is `petplanet_admin` (not `postgres`): `docker exec pet-planet-postgres psql -U petplanet_admin -d <db>`.
- Fulfillment `shipment-created-events.DLT` holds the Phase 1 poison message (`{this is not json`) re-read by the new projection group: expected.
- Headless Chrome after typing a password into the login form swallows later input events in that tab (native password prompt; flags did not help). UI scripts must continue in a new tab (same profile keeps localStorage).
- Dev-mode React StrictMode runs effects twice: data-loaded buttons re-render once just after load; UI scripts must wait ~0.5 s before clicking.
- The `frontend` image copies the source: `docker compose up -d --build frontend` after every frontend change.
- Gateway (Reactor Netty) cached Docker DNS for minutes → 500s after a backend got a new IP on restart. FIXED Phase 6 (`UpstreamDnsConfig`, TTL cap 5 s). Symptom to recognise: gateway log "Connection refused: <service>/<old ip>".
- Postgres has 100 connections for 9 services; Hikari pool capped at 5 (`DB_POOL_SIZE`). Don't raise pools without raising `max_connections`.
- To test a "clean clone" without touching local data: `make down`, copy `git ls-files -co --exclude-standard` into another directory (own Compose project → own volumes), `make up`/`smoke`, then `docker compose down -v --rmi local` there and `make up` here. Container names are fixed, so only one stack can run at a time.
- Reproducing a container IP change: `docker network disconnect pet-planet <c>`, start a placeholder container on the network, reconnect with `--alias <service>`. Leaves half-open DB connections → terminate them in Postgres afterwards.

