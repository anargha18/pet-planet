# 02 · Architecture

## Architectural style

| Principle | How Pet Planet applies it |
|---|---|
| **Microservices** | 10 independently deployable Spring Boot services, each with one clear responsibility |
| **Database per service** | Each domain service owns a private PostgreSQL database. No service reads another's tables |
| **API gateway** | One public entry point (`gateway-service`) for the browser. Backend ports are not published |
| **Event-driven, choreographed saga** | Services communicate state changes through Kafka events. There is no central orchestrator |
| **Transactional outbox** | Events are written to a table in the same transaction as the state change, then relayed to Kafka |
| **CQRS read model** | `fulfillment-service` builds a per-order tracking view purely from events |
| **Zero-trust between hops** | The gateway validates the JWT, and every service validates it again and enforces ownership itself |
| **Shared infrastructure, not shared domain** | `platform-commons` holds Kafka, outbox, security and error conventions. Services never share domain or event classes |

## System context and communication

```mermaid
flowchart LR
  Browser["React storefront :5173"]
  Gateway["API Gateway :8089"]
  Users["user-service :8088"]
  Catalog["pet-catalog-service :8080"]
  Orders["order-service :8081"]
  Inventory["inventory-service :8082"]
  Payment["payment-service :8083"]
  Notify["notification-service :8084"]
  Ship["shipping-service :8085"]
  Delivery["delivery-tracking-service :8086"]
  Track["fulfillment-service :8087"]
  Kafka[("Kafka :9092")]

  Browser -->|"REST + Bearer JWT"| Gateway
  Gateway -->|"/auth, /users + internal token"| Users
  Gateway -->|/pets| Catalog
  Gateway -->|/orders| Orders
  Gateway -->|/inventory| Inventory
  Gateway -->|/payments| Payment
  Gateway -->|/notifications| Notify
  Gateway -->|/shipments| Ship
  Gateway -->|/deliveries| Delivery
  Gateway -->|/fulfillment| Track
  Orders -->|"sync GET /api/v1/pets/id"| Catalog

  Catalog -.-> Kafka
  Orders -.-> Kafka
  Inventory -.-> Kafka
  Payment -.-> Kafka
  Notify -.-> Kafka
  Ship -.-> Kafka
  Delivery -.-> Kafka
  Kafka -.-> Catalog & Orders & Inventory & Payment & Notify & Ship & Delivery & Track
```

Solid arrows are synchronous HTTP; dotted arrows are asynchronous Kafka events. The full producer → topic → consumer map is in [03 Events and the checkout saga](03-events-and-saga.md#topic-map).

There is exactly **one synchronous service-to-service call**: order-service asks pet-catalog-service for each pet at checkout, to check that it is `AVAILABLE` and to snapshot its name, breed and price into the order. Everything else between services is an event.

## Service responsibilities

| Service | Owns | Does **not** own | Talks to |
|---|---|---|---|
| gateway-service | CORS, JWT validation, coarse role rules, routing, correlation IDs | Users, passwords, tokens, per-record ownership | All services over HTTP |
| user-service | Accounts, BCrypt hashes, roles, active flag, token issuance | — | Gateway only (internal token required) |
| pet-catalog-service | Pet name, species, breed, age, gender, price, description, archive | Availability (keeps a read-only copy) | Emits `pet-catalog-events`; consumes `inventory-status-events` |
| order-service | Orders, item snapshots, order state machine | Shipping or delivery progress | Calls catalog; saga events |
| inventory-service | Availability of each unique pet: reserve, expire, sell, release, retire | Prices, descriptions | Saga events, catalog events |
| payment-service | Payment attempts, outcomes, refunds, closed-order markers | Order state | Saga events |
| notification-service | Typed customer notifications (8 types) | — | Consumes outcome events, emits `notification-sent-events` |
| shipping-service | Shipment, tracking number, mirrored shipment status | The "delivered" action | Consumes order-confirmed and delivery events |
| delivery-tracking-service | Carrier progress, delivery timeline, the admin status action | Shipment creation | Consumes shipment events, emits delivery events |
| fulfillment-service | Order-tracking projection + timeline + escalation flag | Any other service's data | Consumes 12 topics, emits nothing |

## Deployment view (local Docker Compose)

```mermaid
flowchart TB
  subgraph Host["Your machine"]
    P5173["localhost:5173"]
    P8089["localhost:8089"]
    P8090["localhost:8090 (optional)"]
    P5432["localhost:5432"]
    P9092["localhost:9092"]
  end
  subgraph Net["Docker network: pet-planet"]
    FE["frontend<br/>node:20 · Vite dev server"]
    GW["gateway-service"]
    subgraph Svc["9 domain services (384 MB each)"]
      direction LR
      S1["user"] --- S2["pet-catalog"] --- S3["order"]
      S4["inventory"] --- S5["payment"] --- S6["notification"]
      S7["shipping"] --- S8["delivery-tracking"] --- S9["fulfillment"]
    end
    PG[("postgres:16-alpine<br/>volume postgres-data")]
    KF[("apache/kafka:4.0.1<br/>volume kafka-data")]
    KUI["kafka-ui (profile tools)"]
  end
  P5173 --> FE
  P8089 --> GW
  P8090 --> KUI
  P5432 --> PG
  P9092 --> KF
  GW --> Svc
  Svc --> PG
  Svc --> KF
  KUI --> KF
```

13 containers start with `make up` (Postgres, Kafka, 10 Spring services, frontend). Every Spring service has a health check, and `depends_on` waits for real readiness. Sizing, ports and variables: [10 Infrastructure and operations](10-infrastructure-and-operations.md).

## How a request travels (request/response flow)

### Layers a request passes through

```mermaid
flowchart LR
  A["Browser<br/>axios client adds<br/>Authorization: Bearer"] --> B
  subgraph GW["gateway-service (WebFlux)"]
    B["CORS<br/>(first in chain)"] --> C["JWT validation<br/>RS256 · iss · aud · exp"] --> D["Coarse role rules<br/>public · user · admin"] --> E["CorrelationIdWebFilter<br/>X-Correlation-Id"] --> F["ForwardedHeadersFilter<br/>strip spoofed headers"] --> G["Route by path prefix"]
  end
  G --> H
  subgraph SVC["domain service (Spring MVC)"]
    H["CorrelationIdFilter<br/>→ logging MDC"] --> I["JWT validation again<br/>(platform-commons)"] --> J["URL rules<br/>(SecurityConfig)"] --> K["@RestController<br/>validate DTO"] --> L["Application service<br/>@Transactional<br/>ownership check"] --> M["Repository → PostgreSQL<br/>+ outbox row"]
  end
  L --> N["Response DTO<br/>(or ApiError JSON)"]
```

### Example 1: browsing the catalog (public)

```mermaid
sequenceDiagram
  autonumber
  participant B as Browser
  participant G as Gateway
  participant C as pet-catalog-service
  participant DB as petplanet_catalog
  B->>G: GET /api/v1/pets?species=DOG&page=0
  Note over G: CORS ok, path is public, no token needed
  G->>C: GET /api/v1/pets?species=DOG&page=0 (X-Correlation-Id)
  C->>DB: SELECT pets WHERE species and status not RETIRED
  DB-->>C: rows
  C-->>G: 200 Page of PetResponse
  G-->>B: 200 + X-Correlation-Id
```

### Example 2: signing in

```mermaid
sequenceDiagram
  autonumber
  participant B as Browser
  participant G as Gateway
  participant U as user-service
  participant DB as petplanet_user
  B->>G: POST /api/v1/auth/login {email, password}
  G->>G: strip client X-Internal-Service-Token and X-Authenticated-User-* headers
  G->>U: POST /api/v1/auth/login + X-Internal-Service-Token
  U->>U: InternalServiceKeyFilter checks token (constant time)
  U->>DB: find active user by email
  U->>U: BCrypt verify, sign RS256 JWT (sub=userId, email, role)
  U-->>G: 200 AuthResponse {token, id, email, role}
  G-->>B: 200 AuthResponse
  Note over B: token and session saved in localStorage (pet-planet-auth)
```

### Example 3: checkout (HTTP part, then the saga takes over)

```mermaid
sequenceDiagram
  autonumber
  participant B as Browser
  participant G as Gateway
  participant O as order-service
  participant C as pet-catalog-service
  participant DB as petplanet_order
  participant R as Outbox relay (in order-service)
  participant K as Kafka
  B->>G: POST /api/v1/orders {items:[{petId, quantity:1}]} + Bearer JWT
  G->>G: validate JWT, role USER or ADMIN
  G->>O: forward (JWT unchanged, X-Correlation-Id)
  O->>O: validate JWT again, customerId = token sub
  loop each pet
    O->>C: GET /api/v1/pets/{petId} (timeouts 2 s connect, 5 s read)
    C-->>O: PetResponse (must be AVAILABLE)
  end
  O->>DB: BEGIN, INSERT order (CREATED) + items, INSERT order_outbox (OrderCreated), COMMIT
  O-->>G: 201 OrderResponse
  G-->>B: 201 OrderResponse
  Note over B: opens /orders/{id} and polls the tracking view every 2 s
  R->>DB: SELECT unpublished FOR UPDATE SKIP LOCKED (every 500 ms)
  R->>K: send to order-created-events, wait for ack
  R->>DB: SET published_at
  Note over K: inventory, payment, order, shipping... continue the saga (chapter 03)
```

The HTTP request returns as soon as the order is saved. Everything afterwards is asynchronous, which is why the order page polls `GET /api/v1/fulfillment/orders/{id}` until the order reaches a stable state. See [03 Events and the checkout saga](03-events-and-saga.md).

### Example 4: a customer opens another customer's order

```mermaid
sequenceDiagram
  autonumber
  participant B as Browser (user 7)
  participant G as Gateway
  participant P as payment-service
  B->>G: GET /api/v1/payments/order/42 + JWT(sub=7)
  Note over G: valid token, path needs USER or ADMIN, so it forwards (no ownership logic at the edge)
  G->>P: forward
  P->>P: load payment for order 42 (customer_id = 9)
  P->>P: AuthenticatedCaller.requireAccessTo(9) fails
  P-->>G: 403 ApiError {status, error, message, path, timestamp, correlationId}
  G-->>B: 403 (with CORS headers)
```

## Cross-cutting concerns

| Concern | Implementation | Chapter |
|---|---|---|
| Authentication | RS256 JWT issued by user-service, validated by all 10 services | [08](08-security.md) |
| Authorization | Coarse role rules at the gateway, ownership checks in each service | [08](08-security.md) |
| Error format | One `ApiError` JSON body from every service and the gateway | [05](05-api-reference.md#errors) |
| Correlation / tracing | `X-Correlation-Id` header → MDC `[corr=...]` in logs; `correlationId` Kafka header per saga | [03](03-events-and-saga.md) |
| Reliable messaging | Transactional outbox, broker-ack relay, retries, DLT, idempotent consumers | [03](03-events-and-saga.md) |
| Schema management | Flyway per service, Hibernate `validate` | [06](06-database.md) |
| Health | `/actuator/health` used by Docker health checks | [10](10-infrastructure-and-operations.md) |
| Code boundaries | ArchUnit (backend) and `check:boundaries` (frontend) | [09](09-code-structure-and-patterns.md) |
