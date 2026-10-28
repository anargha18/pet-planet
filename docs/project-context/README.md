# Pet Planet project context

Pet Planet is a pet-commerce learning project comprising nine Java/Spring Boot services, a React/Vite storefront, PostgreSQL databases, and Kafka messaging. The code includes catalog, order, inventory, payment simulation, user authentication, notification records, shipping, delivery tracking, and fulfillment operations.

## Documentation map

| File | Consult when working on |
|---|---|
| [Architecture](./architecture.md) | Service boundaries, communication, event workflows, design decisions |
| [Backend overview](./backend/README.md) | Java service inventory, shared conventions, and links to service-specific notes |
| [Frontend](./frontend.md) | React routes, state, API calls, and UI integration |
| [Database](./database.md) | Flyway schema, table constraints, ORM mappings, persistence |
| [API reference](./api-reference.md) | HTTP routes, payloads, validation, access, and responses |
| [Infrastructure](./infrastructure.md) | Docker, ports, configuration variables, local setup, operations |
| [Security](./security.md) | JWT, roles, password handling, CORS/CSRF, observed gaps |
| [Testing](./testing.md) | Test inventory, commands, and verification limits |
| [Implementation status](./implementation-status.md) | Feature state, missing integrations, limitations, open questions |

For a backend service task, first read [backend/README.md](./backend/README.md), then its specific service page. For cross-service changes, also consult [architecture](./architecture.md), [database](./database.md), [API reference](./api-reference.md), and [implementation status](./implementation-status.md) as relevant.

## Technology snapshot

- Java 17, Spring Boot 3.5.5, Maven, Spring Data JPA/Hibernate, Flyway, Spring Kafka.
- PostgreSQL 16 Alpine and Apache Kafka 4.0.1 in the local Compose configuration.
- React 18.3.1, React Router 6.28.0, Vite 5.4.10, Material UI 5.16.x, Axios 1.7.x.
- Spring Security, JJWT 0.12.6, and BCrypt in user-service.

Frontend package versions are declared semver ranges; the lockfile exists, but installed versions have not been verified. See [infrastructure](./infrastructure.md) for configuration and version context.

## Current overall status

The catalog and user APIs, order persistence, inventory operations, event consumers/producers, and downstream record services are present. The storefront integrates with catalog, auth, and order APIs, but checkout is not a complete working saga: order creation does not publish the event that begins inventory reservation, and user UUIDs do not match the numeric customer IDs expected by orders. Payment is simulated at a fixed amount; notifications and shipping are local records, not external provider integrations. See [implementation status](./implementation-status.md).

## Verification limitations

The live database was not inspected; migrations are the physical-schema evidence. Services, Docker containers, builds, and tests were not run during the documentation review. “Implemented” describes code/configuration presence, not verified end-to-end operation. Existing worktree changes may be present; inspect `git status` before editing.

