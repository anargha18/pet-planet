# Pet Planet

An event-driven pet shop built to learn microservices: ten Spring Boot services, Kafka, one PostgreSQL database per service, and a React storefront. Every pet is unique, so buying one is a small distributed transaction (a **saga**) across inventory, payment and order, with compensation when anything goes wrong.

Java 17 · Spring Boot 3.5.5 · Spring Cloud Gateway · PostgreSQL 16 · Apache Kafka 4.0.1 (KRaft) · React 18 / Vite / Material UI.

## Quickstart

**Prerequisites:** Docker Desktop with **at least 4 GB of memory (6 GB recommended)**, and `make`. To run tests you also need JDK 17. You don't need Maven, because the repo ships a Maven wrapper. The UI check also needs Node 20 and Chrome.

```sh
make up      # first run: creates .env with fresh local secrets, builds every image, starts, waits until healthy
```

Then open **http://localhost:5173** and sign in:

| Account | Email | Password |
|---|---|---|
| Customer | `customer@petplanet.com` | `password123` |
| Admin | `admin@petplanet.com` | `password123` |

Six sample pets are seeded. As a customer: add a pet to the cart, check out, and watch the order page update live until it is confirmed and shipped. As the admin, open **Admin → Deliveries** and mark the order delivered. The customer's order page then shows the full timeline and every notification. Payment is **simulated**: no card is needed, and the order total is charged automatically.

| What | Where |
|---|---|
| Storefront | http://localhost:5173 |
| API gateway (the only published API) | http://localhost:8089/api/v1/... |
| Kafka UI (topics, messages, dead-letter topics) | `make kafka-ui`, then http://localhost:8090 |

### Everyday commands

```sh
make ps                       # container status and health
make logs s=order-service     # follow one service's logs
make down                     # stop, keeping all data
make reset                    # stop and DELETE all local data (Postgres + Kafka volumes), then start fresh
make infra                    # only Postgres + Kafka, to run services from your IDE (hybrid mode, see infrastructure docs)
make help                     # every target
```

### Checks

```sh
make test          # all backend tests: unit, slice, ArchUnit and Testcontainers (Docker must be running)
make verify        # the same plus packaging: the acceptance build
make smoke         # end-to-end checks against the running stack (3–7 min; restarts some services with test settings)
make ui-check      # headless-Chrome walkthrough of every customer and admin page against the running stack
make frontend-check  # frontend build + folder-boundary check
```

A Postman collection with the same happy path is in [`postman/collections`](postman/collections).

### Secrets and old data

- No keys are committed. `make up` runs `scripts/generate-dev-keys.sh`, which writes a fresh RSA key pair and an internal gateway token into `.env`. It keeps existing values, so tokens stay valid across restarts. If your `.env` still contains the old shared development key, rotate it once with `scripts/generate-dev-keys.sh --force` and sign in again.
- If Flyway refuses to start with a checksum error on an old local database, run `make reset` once. Early commits edited migrations that had already been applied; that no longer happens.

## How it works

```
Browser ──► gateway :8089 ──► user · pet-catalog · order · inventory · payment · notification · shipping · delivery-tracking · fulfillment
              (JWT check,           each service: own PostgreSQL database, own JWT check and ownership rules
               CORS, routing)                    │ events via a transactional outbox
                                                  ▼
                                               Kafka  (one topic per event type, <topic>.DLT for poison messages)
```

- **user-service** owns accounts and is the only service that signs JWTs (RS256). The **gateway** only validates tokens, applies coarse role rules and routes. Every service validates the token again and checks that a customer only sees their own records.
- **Checkout saga** (choreographed, no central coordinator):
  1. order-service saves the order (`CREATED`) and emits `OrderCreated`.
  2. inventory reserves **all** pets or none → `PetReserved` (order → `PAYMENT_PENDING`) or `PetReservationFailed` (order → `FAILED`).
  3. payment charges the total (simulated) → `PaymentSucceeded` or `PaymentFailed` (order → `FAILED`).
  4. inventory sells the pets **only if they are still reserved for this order** → `PetsSold` (order → `CONFIRMED`). If they are not still reserved, inventory emits `InventorySaleConflict` (order → `FAILED`).
  5. shipping creates a shipment → delivery-tracking starts `IN_TRANSIT` → an admin advances it to `OUT_FOR_DELIVERY`, `DELIVERED` or `DELIVERY_FAILED`. Shipping mirrors the status, and notification tells the customer.
- **Compensation:** a customer cancel or a reservation expiry → `OrderCancelled`. Failures → `OrderFailed`. Inventory releases the pets, and payment refunds a successful charge, so no charge is kept for an order that did not complete.
- **notification** sends one typed message per order and event (confirmed, cancelled, failed, refunded, shipped, out for delivery, delivered, delivery failed).
- **fulfillment** is the **order-tracking read model** (CQRS): it listens to every order, payment, shipment, delivery and notification event and serves one view per order, plus a timeline. It escalates delivery failures and orders stuck before confirmation.
- **Reliability:** services write events to an outbox table in the same database transaction as the state change. A shared relay publishes them after the broker acknowledges. Consumers are idempotent, and failing messages are retried and then dead-lettered.
- **Code layout:** every service uses the same packages (`api`, `application`, `domain`, `messaging`, `persistence`, `security`, `client`, `config`), enforced by ArchUnit. The frontend is grouped by feature, enforced by `npm run check:boundaries`.

## Documentation

Start at the **[documentation index](docs/project-context/README.md)**: architecture, event catalog, API reference, database, security, infrastructure, frontend, testing and implementation status. The restructuring that produced this design is described in [docs/restructuring](docs/restructuring/02-target-architecture-and-requirements.md).
