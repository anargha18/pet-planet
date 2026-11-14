# NOW — restructuring checkpoint

**Updated:** 2026-11-14 · **Branch:** `config-analysed` (nothing pushed) · **HEAD:** end of Phase 3 (see git log)
**Working tree at checkpoint:** clean after the Phase 3 commits.

## Where we are

Restructuring per `docs/restructuring/02-target-architecture-and-requirements.md` (requirement IDs `R-…`, phase plan in §4, detailed per-phase log in "Progress log").

| Phase | Status | Verified by |
|---|---|---|
| 0a Baseline + internal structure | **Done** | `./mvnw test` same 49 functional tests; ArchUnit; `docker compose build` |
| 0b Make it run (user-service issues JWT, gateway CORS, seed passwords, healthchecks, Makefile) | **Done** | Live: browser-equivalent login/CORS checks, all containers healthy |
| 1 Event backbone (`platform-commons`, outbox relay, DLT, idempotency) | **Done** | Live order → CONFIRMED path; poison message → `.DLT`; real-Kafka test |
| 2 Saga correctness (state machine, sell-only-if-reserved, refunds, archive) | **Done** | `make smoke` 22/22 on 2026-11-14; 205 tests pass |
| 3 Security consolidation | **Done** | `./mvnw test` green; `make smoke` 49/49 (incl. ownership scenario S) on 2026-11-14 |
| 4 Downstream boundaries (shipping/delivery/fulfillment/notification) | Not started | — |
| 5 Frontend alignment | Not started | — |
| 6 Verify + document | Not started (parts of R-TEST-04 / R-DOC done early) | — |

Last full verification (end of Phase 3): `./mvnw test` BUILD SUCCESS, all 11 modules; `npm run check:boundaries` + `npm run build` OK; `make smoke` → 49 passed, 0 failed. Stack up and healthy with rotated local keys.

## Commits by phase (oldest → newest)

- Checkpoint/docs: `da52467` WIP snapshot, `761a7f9` audit + plan, `d941ecb` structure decision
- 0a: `1369362` … `694c11d` (root POM/wrapper, per-service re-package steps A+B, ArchUnit, frontend by feature)
- 0b: `0162ea5` … `24ac5b2`
- 1: `59f672e` … `8ac25a9`
- 2: `b0f791a` … `499990a`
- 3: see `git log` after `499990a` (memory setup, platform-commons security/web, services, infra keys, smoke, docs)

## Next action (Phase 4 — Downstream boundaries)

Requirements DEC-2, R-SAGA-06, R-SAGA-09, R-SVC-01…05 (plan §3/§4): one delivered endpoint (S9), fulfillment as tracking projection, notification per event type, consumers for orphan topics (S11), admin delivery step in smoke.
**Owner approval for Phase 4 not yet given.** Phase 3 details: plan "Progress log" → Phase 3; decisions in DECISIONS.md.
