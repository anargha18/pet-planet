# Infrastructure and local development

## Docker Compose topology

`docker-compose.yml` runs PostgreSQL 16 (`:5432`), Apache Kafka 4.0.1 KRaft (`:9092`), ten Spring Boot containers on the `pet-planet` bridge network, and the React/Vite frontend (`:5173`). Only `gateway-service` (`:8089`) is published as an application API; backend service ports remain internal to the Docker network.

## Environment variables & RS256 keys

- `.env.example` and `docker-compose.yml` configure the RS256 keypair (`JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY`) alongside `JWT_ISSUER=pet-planet`, `JWT_AUDIENCE=pet-planet`, and `JWT_EXPIRATION_MS=3600000`.
- Only `gateway-service` receives the private signing key. Services that retain defense-in-depth JWT verification receive the public key.
- `gateway-service` is configured with upstream URLs for all nine backend domain services. The user service additionally requires `USER_SERVICE_INTERNAL_TOKEN`, shared only with the gateway in local Compose.
- The checked-in internal token fallback is development-only; set a randomly generated secret in `.env` for local deployments and configure mTLS for production service-to-service traffic.

## Image builds

- Each service Dockerfile builds its jar with Maven inside a `maven:3.9.11-eclipse-temurin-17` stage, using a shared BuildKit cache mount (`id=pet-planet-m2`). Libraries are downloaded once and reused across all service images and rebuilds. `docker compose build` requires BuildKit (the default in current Docker).
- Outside Docker, use the root Maven wrapper: `./mvnw test` (all services) or `./mvnw -pl <service> test`.
