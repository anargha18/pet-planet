# Testing

## Test inventory

| Module | Test files |
|---|---|
| `pet-catalog-service` | `PetServiceTest` (verifies pet creation defaults to `AVAILABLE`) |
| `order-service` | `OrderCommandServiceTest` (order creation, pricing, validation, catalog errors, cancellation), `OrderQueryServiceTest` (lookup, not-found, owner access control), `OrderSagaServiceTest` (payment success/failure handling) and `OrderControllerTest` (`@WebMvcTest` with Spring Security) |
| `inventory-service` | `InventoryServiceTest` (reservation, optimistic locking conflict, idempotent `OrderCreatedEvent` & `OrderConfirmedEvent` handling) |
| `payment-service` | `PaymentServiceTest` (simulated payment success/failure, idempotency, outbox persistence) |
| `notification-service` | `NotificationServiceTest` (confirmation notification creation and duplicate event safety) |
| `shipping-service` | `ShippingServiceTest` (shipment and tracking number creation and idempotency) |
| `delivery-tracking-service` | `DeliveryTrackingServiceTest` (delivery tracking creation and idempotency) |
| `fulfillment-service` | `FulfillmentServiceTest` (initial `SHIPPED` projection creation, update to `DELIVERED` on subsequent event, and idempotency) |
| `gateway-service` | `JwtServiceTest` (RS256 token claims, validation, and tampering) and `GatewayAuthFilterTest` (identity header overwrite, admin-path and customer-scope rejection) |
| `user-service` | `UserControllerTest` (internal service-token protection and integer-ID profile lookup), `AuthServiceTest` (registration always creates a `USER`) and `UserServiceTest` (last-admin protection) |
| every service | `ArchitectureTest`: 11 ArchUnit package-boundary rules per service (see [Code structure](./code-structure.md)) |

All tests are unit or slice tests with mocks. There are no integration tests against real Kafka or PostgreSQL yet (planned: R-TEST-01 in the [restructuring plan](../restructuring/02-target-architecture-and-requirements.md)).

## Commands

- All backend services from the repository root: `./mvnw test` (Maven wrapper; no global Maven needed). One service: `./mvnw -pl order-service test`.
- Frontend production build: `cd frontend && npm run build`. Frontend folder-boundary check: `npm run check:boundaries`.
