# Backend services

All ten backend modules use Java 17 and Spring Boot 3.5.5.

| Service | Port | Notes |
|---|---:|---|
| [API Gateway](./gateway-service.md) | 8089 | RS256 authentication, edge authorization, routing, and global CORS |
| [Pet catalog](./pet-catalog-service.md) | 8080 | Catalog CRUD/search, seeded pets, and availability sync consumer |
| [Order](./order-service.md) | 8081 | Order API, synchronous catalog client, saga event producer/consumer, outbox poller |
| [Inventory](./inventory-service.md) | 8082 | Atomic multi-pet reservation, expiry scheduler, rollback consumers, status sync |
| [Payment](./payment-service.md) | 8083 | Event-driven payment processing using order `totalAmount` + lookup REST API |
| [Notification](./notification-service.md) | 8084 | Order confirmation notifications with propagated `customerId` |
| [Shipping](./shipping-service.md) | 8085 | Shipment creation, tracking numbers, and manual delivered transition |
| [Delivery tracking](./delivery-tracking-service.md) | 8086 | Delivery state tracking and status events |
| [Fulfillment](./fulfillment-service.md) | 8087 | Operational fulfillment projection with live status updates |
| [User](./user-service.md) | 8088 (internal) | Account CRUD, BCrypt credentials, and seeded users |
