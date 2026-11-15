# Infrastructure and local development

## Docker Compose topology

`docker-compose.yml` runs PostgreSQL 16 (`:5432`), Apache Kafka 4.0.1 KRaft (`:9092`), ten Spring Boot containers on the `pet-planet` bridge network, and the React/Vite frontend (`:5173`). Only `gateway-service` (`:8089`) is published as an application API; backend service ports remain internal to the Docker network.

- **Health and startup order:** every Spring service has a Compose healthcheck (`curl -fsS http://localhost:<port>/actuator/health`). `depends_on` waits for real dependencies to be healthy: Postgres and Kafka for every service; catalog for order; user, catalog, order and inventory for the gateway; the gateway for the frontend. `make up` runs `docker compose up -d --build --wait --wait-timeout 600`.
- **Memory:** sized for a ~4 GB Docker Desktop VM. Each Spring service runs with `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k` and `mem_limit` `SERVICE_MEM_LIMIT` (default `384m`). Kafka is capped at 640 MB (heap 384 MB), Postgres at 256 MB, the frontend at 512 MB. Measured on 2026-11-14 with the stack idle after startup: about 3.1 GB in total, with no OOM kills. 6 GB of Docker memory is recommended.
- **Kafka UI (optional):** `make kafka-ui` starts `ghcr.io/kafbat/kafka-ui:v1.2.0` (Compose profile `tools`, 384 MB cap) at http://localhost:8090 for browsing topics, messages and `<topic>.DLT` dead-letter topics. It is not started by `make up`.
- **Make targets:** `keys` (create `.env` and generate local secrets; also run by `up` and `infra`), `up`, `down`, `reset` (deletes volumes), `kafka-ui`, `infra` (Postgres + Kafka only, for running services from an IDE with their `application.yml` localhost defaults; import the root `pom.xml` so the IDE builds `platform-commons` too, or run `./mvnw install -pl platform-commons` once), `build`, `logs s=<service>`, `ps`, `test`, `smoke` (end-to-end saga checks), `frontend-check`.
- **Saga test knobs** (defaults in brackets; used by `make smoke`): `PAYMENT_SIMULATION_RESULT` [`SUCCESS`], `PAYMENT_SIMULATION_DELAY` [`PT0S`], `INVENTORY_RESERVATION_TIMEOUT` [`PT5M`], `INVENTORY_EXPIRATION_CHECK_DELAY_MS` [`30000`], `FULFILLMENT_STUCK_AFTER` [`PT10M`], `FULFILLMENT_ESCALATION_CHECK_MS` [`60000`].
- **Database connections:** all nine services share one PostgreSQL (`max_connections` 100, 3 reserved for superusers). The shared `x-java-env` anchor sets `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` to `DB_POOL_SIZE` (default `5`), so the stack holds about 45 connections. Hikari's default of 10 per service used 90 and left no room for restarts or a service run from the IDE (found in Phase 6).
- **Gateway DNS:** the gateway's HTTP client caches upstream DNS answers for at most `gateway.upstream-dns-cache-ttl` (default `PT5S`, `routing/UpstreamDnsConfig`). Without that cap, a backend restarted onto a new container IP stayed unreachable through the gateway for minutes (verified and fixed in Phase 6).
- **CORS origins:** `FRONTEND_ORIGIN` (comma-separated, default `http://localhost:5173,http://127.0.0.1:5173`) is read by the gateway's CORS configuration (`CorsConfig`).

## Environment variables & RS256 keys

- **No secrets in git (R-INF-08).** `.env.example` leaves `JWT_PUBLIC_KEY`, `JWT_PRIVATE_KEY` and `USER_SERVICE_INTERNAL_TOKEN` empty. `scripts/generate-dev-keys.sh` (run by `make keys`, `make up`, `make infra`) creates `.env` from `.env.example` if missing and fills them: an RSA-2048 pair via `openssl` (PKCS#8 private / X.509 public, base64, one line each) and a random 32-byte hex token. Existing values are kept; `--force` rotates them (users then log in again).
- `docker-compose.yml` refuses to start without them (`${VAR:?…}`), with a message naming the script. `JWT_ISSUER` (default `pet-planet`) and `JWT_AUDIENCE` (default `pet-planet`) are shared via the `x-jwt-verify-env` anchor, which gives **all 10 Spring services** `JWT_PUBLIC_KEY`. Only `user-service` additionally receives `JWT_PRIVATE_KEY` and `JWT_EXPIRATION_MS` (default `3600000`).
- `application.yml` files contain no key defaults (`jwt.public-key: ${JWT_PUBLIC_KEY:}`); a service started without a key fails at startup. For IDE/hybrid runs, export the values from `.env`.
- `gateway-service` is configured with upstream URLs for all nine backend domain services. The user service additionally requires `USER_SERVICE_INTERNAL_TOKEN`, shared only with the gateway. Production service-to-service traffic should use mTLS or workload identity (not configured here).

## Image builds

- Each service Dockerfile builds from the **repository root** context (Compose `context: .`, `dockerfile: <service>/Dockerfile`) with `mvn -pl <service> -am package`, so the root reactor POM and `platform-commons` are available (R-INF-02). `.dockerignore` excludes `frontend/`, `docs/`, `postman/`, build output and secrets.
- Maven runs inside a `maven:3.9.11-eclipse-temurin-17` stage with a shared BuildKit cache mount (`id=pet-planet-m2`). Libraries are downloaded once and reused across all service images and rebuilds. `docker compose build` requires BuildKit (the default in current Docker).
- Outside Docker, use the root Maven wrapper: `./mvnw test` (all services) or `./mvnw -pl <service> -am test` (`-am` builds `platform-commons` first).
