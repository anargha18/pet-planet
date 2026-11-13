# Testing

## Test inventory

| Module | Test files |
|---|---|
| `pet-catalog-service` | `PetServiceTest` (verifies pet creation defaults to `AVAILABLE`) |
| `order-service` | `OrderServiceTest` (order creation, pricing, access control, cancellation, payment event handling) and `OrderControllerTest` (`@WebMvcTest` with Spring Security) |
| `inventory-service` | `InventoryServiceTest` (reservation, optimistic locking conflict, idempotent `OrderCreatedEvent` & `OrderConfirmedEvent` handling) |
| `payment-service` | `PaymentServiceTest` (simulated payment success/failure, idempotency, outbox persistence) |
| `notification-service` | `NotificationServiceTest` (confirmation notification creation and duplicate event safety) |
| `shipping-service` | `ShippingServiceTest` (shipment and tracking number creation and idempotency) |
| `delivery-tracking-service` | `DeliveryTrackingServiceTest` (delivery tracking creation and idempotency) |
| `fulfillment-service` | `FulfillmentServiceTest` (initial `SHIPPED` projection creation, update to `DELIVERED` on subsequent event, and idempotency) |
| `gateway-service` | `JwtServiceTest` (RS256 token claims, validation, and tampering) |
| `user-service` | `UserControllerTest` (internal service-token protection and integer-ID profile lookup) and `UserServiceTest` (registration role and last-admin protection) |
