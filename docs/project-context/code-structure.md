# Code structure and boundaries

All 10 services stay as top-level folders at the repository root. Inside each service and inside the frontend, **the folder a file lives in defines its job**. Automated checks enforce the boundaries: an ArchUnit `ArchitectureTest` in every service and `npm run check:boundaries` in the frontend.

## Repository root

| Path | Purpose |
|---|---|
| `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/` | Root Maven aggregator and wrapper: `./mvnw test` builds and tests all services without a global Maven install. Services still use `spring-boot-starter-parent` directly. |
| `platform-commons/` | Shared **infrastructure-only** library (no domain or event classes): Kafka conventions (JSON converter, retries + `<topic>.DLT`, MDC correlation) and the transactional-outbox relay (`OutboxMessage`, `OutboxStore`, `OutboxRelay`), applied via Spring Boot auto-configuration. Services may depend on `com.petcommerce.platform`, never on each other. |
| `<name>-service/` (×10) | One deployable Spring Boot service each, with its own `pom.xml`, `Dockerfile` and Flyway migrations. |
| `frontend/` | React storefront. |
| `infrastructure/postgres/init/` | Database and role creation for the local Postgres container. |
| `docs/` | Project documentation (`project-context/` is canonical). |

## Inside a service (`com.petcommerce.<svc>`)

| Package | Contains | May depend on |
|---|---|---|
| `api.controller` | Thin `@RestController` classes: validate input → call `application` → return DTOs | `application`, `api.dto`, `api.mapper`, `domain.model` (enums in parameters) |
| `api.dto` | Request/response records | — |
| `api.mapper` | Entity → response DTO conversion (static methods) | `api.dto`, `domain.model` |
| `api.error` | `@RestControllerAdvice` exception → HTTP mapping, error body | `domain.exception` |
| `application` | Business use cases, transactions, idempotency. One class per responsibility (e.g. `OrderCommandService`, `OrderQueryService`, `OrderSagaService`; `AuthService` vs `UserService`) | `domain`, `persistence`, `messaging.publisher`, `messaging.event`, `security` (access policies), `client`, `api.dto` + `api.mapper` (see note) |
| `domain.model` | JPA entities, enums | nothing in the service outside `domain` |
| `domain.policy` | Pure business rules (e.g. `EmailPolicy`) | `domain` only |
| `domain.exception` | Business exceptions (all `RuntimeException` subclasses of the service live here) | `domain` only |
| `security` | Security filter chain, JWT validation, ownership/access policies (e.g. `OrderAccessPolicy`) | `domain.model` |
| `messaging.event` | This service's own event payload records (incoming and outgoing) | — |
| `messaging.consumer` | `@KafkaListener` adapters: deserialize → call `application` | `application`, `messaging.event` |
| `messaging.publisher` | Builds events and writes them to the outbox (`*EventPublisher`); exposes the outbox table to the shared relay (`*OutboxStore`) | `persistence`, `messaging.event`, `domain.model`, `com.petcommerce.platform` |
| `persistence.repository` | Spring Data repositories and query specifications | `domain.model`, `persistence.entity` |
| `persistence.entity` | Technical tables: outbox events (implement `platform-commons` `OutboxMessage`), processed-event (idempotency) records | `com.petcommerce.platform` |
| `client` | Outbound HTTP to other services (order → catalog) | `domain` |
| `config` | Non-security Spring wiring (Kafka topics, beans) | — |
| `routing` (gateway only) | Gateway route definitions | — |

**Note on DTOs in `application`:** application services return response DTOs (built via `api.mapper` inside the transaction), because entities such as `Order` have lazily loaded collections and `open-in-view` is disabled. Application code must still not depend on controllers, `api.error`, Kafka or Spring Web types.

### Enforced rules (`<svc>/src/test/java/com/petcommerce/<svc>/ArchitectureTest.java`)

1. `domain` depends on nothing else in the service, and not on Spring Web, HTTP or Kafka.
2. `api` never touches `persistence`, `messaging` or `client`.
3. `messaging.consumer` never touches `persistence`, `api`, `client` or `messaging.publisher`.
4. `application` never depends on `api.controller`, `api.error`, `messaging.consumer`, Kafka or Spring Web.
5. Repositories are used only by `application`, `messaging.publisher` and `persistence`.
6. `@RestController` classes live in `api.controller`, `@RestControllerAdvice` classes in `api.error`, `@KafkaListener` methods in `messaging.consumer`.
7. Every `RuntimeException` subclass lives in `domain.exception`.
8. No service imports another service's classes; services share JSON/HTTP contracts only. The one shared dependency is `platform-commons` (`com.petcommerce.platform`).

A violation fails `./mvnw test`.

## Frontend (`frontend/src`)

| Path | Contains |
|---|---|
| `main.jsx`, `index.css` | Entry point and global styles |
| `app/` | `App.jsx`: providers and routes; composes features |
| `api/` | `client.js` (axios instances, `getAuthHeaders`, `extractApiError`) and one module per backend area (`auth`, `catalog`, `users`, `orders`, `tracking`), all re-exported by `api/index.js` |
| `features/<name>/` | `auth`, `catalog`, `cart`, `checkout`, `orders`: each owns its pages, components and state, and exposes a public `index.js` |
| `components/` | Shared, feature-agnostic UI (`Layout`) |
| `theme/` | MUI theme |

Rules (`npm run check:boundaries`, script `frontend/scripts/check-boundaries.mjs`):
1. A feature imports another feature only through its index (`'../cart'`), never its files.
2. Backend calls are imported from the `api` index, never from individual `api/*` modules.
3. Features never import from `app/`.
