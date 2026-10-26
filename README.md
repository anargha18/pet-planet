# Pet Planet

Event-driven pet e-commerce learning project built incrementally with Java 17, Spring Boot, PostgreSQL, Kafka, Docker Compose, and Maven.

## Current architecture

The project includes the core event-driven saga flow across multiple services:

- Pet catalog service for product inventory and pet metadata
- Order service for order lifecycle and saga state transitions
- Inventory service for reservation, expiry, and sold-state tracking
- Payment service with simulated payment outcomes and outbox publishing
- Notification service for order-confirmed handling and customer notification records
- Shipping service for fulfilment handoff and tracking-number creation
- Delivery tracking service for downstream delivery status monitoring and lookup
- Fulfillment operations service for fulfillment status aggregation and operational monitoring
- User service for registration, authentication, JWT issuance, and protected user endpoints

The system uses separate PostgreSQL databases per service, Kafka topics for domain events, and local transactional outboxes to publish events after commit.

## Current step

The user service is now in place to provide secure authentication and authorization, while the downstream event-driven workflow remains intact.

## Start infrastructure

```sh
cp .env.example .env
docker compose up -d
docker compose ps
```

The `.env` file is intentionally untracked and should contain local development credentials.

Connection endpoints from the host:

- PostgreSQL: `localhost:5432`
- Kafka: `localhost:9092`
- Pet Catalog API: `http://localhost:8080/api/v1/pets`
- Order API: `http://localhost:8081/api/v1/orders`
- Inventory API: `http://localhost:8082/api/v1/inventory`
- Payment API: `http://localhost:8083/api/v1/payments`
- Notification API: `http://localhost:8084/api/v1/notifications`
- Shipping API: `http://localhost:8085/api/v1/shipments`
- Delivery Tracking API: `http://localhost:8086/api/v1/deliveries`
- Fulfillment API: `http://localhost:8087/api/v1/fulfillment`
- User API: `http://localhost:8088/api/v1/auth` and `/api/v1/users`

## Stop infrastructure

```sh
docker compose down
```

To remove local persisted data as well:

```sh
docker compose down -v
```
