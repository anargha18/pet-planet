# Infrastructure and local development

## Docker Compose topology

`docker-compose.yml` runs PostgreSQL 16 (`:5432`), Apache Kafka 4.0.1 KRaft (`:9092`), ten Spring Boot containers (`:8080` through `:8089`), and the React/Vite frontend (`:5173`) on the `pet-planet` bridge network.

## Environment variables & RS256 keys

- `.env.example` and `docker-compose.yml` include pre-configured development RS256 keys (`JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY`) alongside `JWT_ISSUER=pet-planet`, `JWT_AUDIENCE=pet-planet`, and `JWT_EXPIRATION_MS=3600000`.
- `user-service` receives both `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY`; `pet-catalog-service`, `order-service`, and `inventory-service` receive `JWT_PUBLIC_KEY`.
- `gateway-service` is configured with upstream URLs for all nine backend domain services.
