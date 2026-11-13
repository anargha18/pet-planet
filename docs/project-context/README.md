# Pet Planet project context

Pet Planet is an event-driven pet e-commerce platform comprising ten Java 17 / Spring Boot 3.5.5 services (including a Spring Cloud Gateway), a React 18 / Vite storefront, nine service-specific PostgreSQL 16 databases, and Apache Kafka 4.0.1 messaging.

## Documentation map

| File | Consult when working on |
|---|---|
| [Architecture](./architecture.md) | Service boundaries, gateway routing, event workflows, saga compensation, outbox relay |
| [Backend overview](./backend/README.md) | Java service inventory, shared conventions, and links to service-specific notes |
| [Frontend](./frontend.md) | React routes, state, API calls, pagination, species filtering, and order tracking UI |
| [Database](./database.md) | Flyway schema, `TIMESTAMPTZ` standardization, seed data migrations, ORM mappings |
| [API reference](./api-reference.md) | HTTP routes via the gateway, payloads, validation, and access rules |
| [Infrastructure](./infrastructure.md) | Docker Compose, ports, RSA JWT environment variables, local setup, operations |
| [Security](./security.md) | RS256 asymmetric JWT signing/verification, roles, BCrypt, CORS/CSRF |
| [Testing](./testing.md) | Unit and slice test inventory and commands |
| [Implementation status](./implementation-status.md) | Current end-to-end feature state and completed refactoring summary |
| [Restructuring: audit findings](../restructuring/01-audit-findings.md) | Verified defects and deviations found on 2026-11-13 (blockers, saga, security, frontend, docs) |
| [Restructuring: target architecture & requirements](../restructuring/02-target-architecture-and-requirements.md) | Approved target design, confirmed decisions, folder-structure rules (`R-STR`), requirement IDs (`R-…`), and phased plan (not yet implemented) |

## Technology snapshot

- Java 17, Spring Boot 3.5.5, Spring Cloud Gateway 2025.0.0, Maven, Spring Data JPA/Hibernate, Flyway, Spring Kafka.
- PostgreSQL 16 Alpine and Apache Kafka 4.0.1 (KRaft mode) in the local Compose configuration.
- React 18.3.1, React Router 6.28.0, Vite 5.4.10, Material UI 5.16.x, Axios 1.7.x.
- Spring Security, JJWT 0.12.6 (RS256 asymmetric keys), and BCrypt.

## Current overall status

The end-to-end checkout saga, compensating rollbacks, catalog-inventory status synchronization, `@Scheduled` transactional outbox pollers, gateway-owned RS256 JWT handling, and Flyway seed data (6 sample pets + default `ADMIN` and `USER` accounts) are wired across the services and React storefront. See [implementation status](./implementation-status.md). **Note:** the 2026-11-13 [audit](../restructuring/01-audit-findings.md) found runtime blockers in this wiring; restructuring is planned in [the requirements doc](../restructuring/02-target-architecture-and-requirements.md).
