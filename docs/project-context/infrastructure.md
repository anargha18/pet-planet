# Infrastructure and local development

## Docker Compose topology

`docker-compose.yml` runs PostgreSQL 16 (`:5432`), Apache Kafka 4.0.1 KRaft (`:9092`), ten Spring Boot containers on the `pet-planet` bridge network, and the React/Vite frontend (`:5173`). Only `gateway-service` (`:8089`) is published as an application API; backend service ports remain internal to the Docker network.

- **Health and startup order:** every Spring service has a Compose healthcheck (`curl -fsS http://localhost:<port>/actuator/health`). `depends_on` waits for real dependencies to be healthy: Postgres and Kafka for every service; catalog for order; user, catalog, order and inventory for the gateway; the gateway for the frontend. `make up` runs `docker compose up -d --build --wait --wait-timeout 600`.
- **Memory:** sized for a ~4 GB Docker Desktop VM. Each Spring service runs with `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k` and `mem_limit` `SERVICE_MEM_LIMIT` (default `384m`). Kafka is capped at 640 MB (heap 384 MB), Postgres at 256 MB, the frontend at 512 MB. Measured on 2026-11-14 with the stack idle after startup: about 3.1 GB in total, with no OOM kills. 6 GB of Docker memory is recommended.
- **Kafka UI (optional):** `make kafka-ui` starts `ghcr.io/kafbat/kafka-ui:v1.2.0` (Compose profile `tools`, 384 MB cap) at http://localhost:8090 for browsing topics, messages and `<topic>.DLT` dead-letter topics. It is not started by `make up`.
- **Make targets:** `up`, `down`, `reset` (deletes volumes), `kafka-ui`, `infra` (Postgres + Kafka only, for running services from an IDE with their `application.yml` localhost defaults; import the root `pom.xml` so the IDE builds `platform-commons` too, or run `./mvnw install -pl platform-commons` once), `build`, `logs s=<service>`, `ps`, `test`, `frontend-check`.
- **CORS origins:** `FRONTEND_ORIGIN` (comma-separated, default `http://localhost:5173,http://127.0.0.1:5173`) is read by the gateway's `CorsWebFilter`.

## Environment variables & RS256 keys

- `.env.example` and `docker-compose.yml` configure the RS256 keypair (`JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY`) alongside `JWT_ISSUER=pet-planet`, `JWT_AUDIENCE=pet-planet`, and `JWT_EXPIRATION_MS=3600000`.
- Only `user-service` receives the private signing key (it issues tokens). The gateway and the services that retain defense-in-depth JWT verification receive the public key.
- `gateway-service` is configured with upstream URLs for all nine backend domain services. The user service additionally requires `USER_SERVICE_INTERNAL_TOKEN`, shared only with the gateway in local Compose.
- The checked-in internal token fallback is development-only; set a randomly generated secret in `.env` for local deployments and configure mTLS for production service-to-service traffic.

## Image builds

- Each service Dockerfile builds from the **repository root** context (Compose `context: .`, `dockerfile: <service>/Dockerfile`) with `mvn -pl <service> -am package`, so the root reactor POM and `platform-commons` are available (R-INF-02). `.dockerignore` excludes `frontend/`, `docs/`, `postman/`, build output and secrets.
- Maven runs inside a `maven:3.9.11-eclipse-temurin-17` stage with a shared BuildKit cache mount (`id=pet-planet-m2`). Libraries are downloaded once and reused across all service images and rebuilds. `docker compose build` requires BuildKit (the default in current Docker).
- Outside Docker, use the root Maven wrapper: `./mvnw test` (all services) or `./mvnw -pl <service> -am test` (`-am` builds `platform-commons` first).
