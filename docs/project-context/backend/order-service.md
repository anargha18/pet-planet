# Order service

**Module:** `order-service` · **Package root:** `com.petcommerce.order` · **Database:** `petplanet_order` (configured name; live database not inspected) · **Port:** 8081 by default.

## Responsibilities and structure

- `controller/OrderController`: create, retrieve, page/filter, cancel.
- `service/OrderService`: order lifecycle and payment-result handling.
- `client/PetCatalogClient`, `PetCatalogPetResponse`: synchronous catalog lookup through Spring `RestClient`.
- `entity/Order`, `OrderItem`, `OrderStatus`, `Species`, `OrderOutboxEvent`: persistence and lifecycle.
- `dto/CreateOrderRequest`, `CreateOrderItemRequest`, `OrderResponse`, `OrderItemResponse`: request/response types.
- `consumer/OrderEventConsumer`: consumes payment success/failure.
- `config/KafkaConfig`: declares payment-result and order-confirmed topics.
- `exception/GlobalExceptionHandler` and domain exceptions: API error conversion.

## Business behavior

Order creation requires a customer ID and a non-empty item list. Each requested pet is fetched from catalog, availability is checked, and catalog data is stored as a snapshot. Quantity is constrained to one; line subtotals and total are calculated from catalog price. `CREATED` is initial status. Cancellation is only allowed in this state.

On payment success, the order is marked `PAID`, and an `OrderConfirmedEvent` is stored in the outbox and sent after commit. Payment failure marks it `FAILED`. `PAYMENT_PENDING` exists in the enum, but no transition into it was found. Order creation does **not** emit `order-created-events`, so it does not initiate the inventory/payment path.

## Persistence, transactions, security

`Order` has a one-to-many cascade-all/orphan-removal mapping to `OrderItem`; item-to-order is lazy. Mutations use transactions. See [database.md](../database.md) for table constraints and [architecture.md](../architecture.md) for event flow.

There is no Spring Security configuration in this service. The numeric `customerId` is trusted from the request and is not bound to user-service identity. API contracts are in [api-reference.md](../api-reference.md); workflow gaps are in [implementation-status.md](../implementation-status.md).

