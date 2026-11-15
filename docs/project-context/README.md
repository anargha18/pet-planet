# Pet Planet project context

Pet Planet is an event-driven pet e-commerce platform comprising ten Java 17 / Spring Boot 3.5.5 services (including a Spring Cloud Gateway), a React 18 / Vite storefront, nine service-specific PostgreSQL 16 databases, and Apache Kafka 4.0.1 messaging.

## Documentation map

| File | Consult when working on |
|---|---|
| [Event catalog](./events.md) | Kafka wire format, outbox relay, retries and dead-letter topics, idempotency, topic ownership and payload fields |
| [Architecture](./architecture.md) | Service boundaries, gateway routing, event workflows, saga compensation, outbox relay |
| [Code structure](./code-structure.md) | Repository layout, per-service package layout, frontend feature folders, and the enforced boundary rules |
| [Backend overview](./backend/README.md) | Java service inventory, shared conventions, and links to service-specific notes |
| [Frontend](./frontend.md) | React routes and route guards, the shared API client and session handling, customer and admin pages, UI verification |
| [Database](./database.md) | Flyway schema, `TIMESTAMPTZ` standardization, seed data migrations, ORM mappings |
| [API reference](./api-reference.md) | HTTP routes via the gateway, payloads, validation, and access rules |
| [Infrastructure](./infrastructure.md) | Docker Compose, ports, RSA JWT environment variables, local setup, operations |
| [Security](./security.md) | RS256 asymmetric JWT signing/verification, roles, BCrypt, CORS/CSRF |
| [Testing](./testing.md) | Test inventory (unit, slice, ArchUnit, Testcontainers, contract), `make smoke`, `make ui-check`, Postman, latest results |
| [Implementation status](./implementation-status.md) | What is verified, by which check, and the known limitations |
| [Restructuring: audit findings](../restructuring/01-audit-findings.md) | Verified defects and deviations found on 2026-11-13 (blockers, saga, security, frontend, docs) |
| [Restructuring: target architecture & requirements](../restructuring/02-target-architecture-and-requirements.md) | Approved target design, confirmed decisions, folder-structure rules (`R-STR`), requirement IDs (`R-…`), phased plan, and the per-phase progress log (all phases done as of 2026-11-15) |

## Technology snapshot

- Java 17, Spring Boot 3.5.5, Spring Cloud Gateway 2025.0.0, Maven, Spring Data JPA/Hibernate, Flyway, Spring Kafka.
- PostgreSQL 16 Alpine and Apache Kafka 4.0.1 (KRaft mode) in the local Compose configuration.
- React 18.3.1, React Router 6.28.0, Vite 5.4.10, Material UI 5.16.x, Axios 1.7.x.
- Spring Security OAuth2 resource server and Nimbus `JwtEncoder` (RS256 asymmetric keys), and BCrypt.

## Current overall status

The restructuring (phases 0a–6) is complete as of 2026-11-15. The checkout saga with compensation, the event backbone (outbox, retries, dead-letter topics, idempotency), service-level security, typed notifications, the order-tracking projection and the storefront with its admin area all work end to end. They are verified by `make verify`, `make smoke` and `make ui-check`. See [implementation status](./implementation-status.md) for the evidence per feature and the known limitations.
