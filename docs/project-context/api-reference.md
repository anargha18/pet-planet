# API reference

All endpoints below are accessible both directly on each service's host port and through **`gateway-service` (`http://localhost:8089`)**.

## API Gateway (`gateway-service`, port 8089)

Routes `/api/v1/auth/**` and `/api/v1/users/**` to `user-service:8088`, `/api/v1/pets/**` to `pet-catalog-service:8080`, `/api/v1/orders/**` to `order-service:8081`, `/api/v1/inventory/**` to `inventory-service:8082`, `/api/v1/payments/**` to `payment-service:8083`, `/api/v1/notifications/**` to `notification-service:8084`, `/api/v1/shipments/**` to `shipping-service:8085`, `/api/v1/deliveries/**` to `delivery-tracking-service:8086`, and `/api/v1/fulfillment/**` to `fulfillment-service:8087`.

## User & Authentication (`user-service`, port 8088)

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `POST /api/v1/auth/register` | JSON: `email`, `password`, `firstName`, `lastName`. | `AuthResponse` (`token`, `tokenType`, `email`, `role`), `201 Created`. Public. |
| `POST /api/v1/auth/login` | JSON: `email`, `password`. | `AuthResponse`, `200 OK`. Public. |
| `GET /api/v1/users/me` | Current authenticated profile. | `UserResponse` (`id`, `email`, `firstName`, `lastName`, `role`), `200 OK`. Bearer JWT required. |
| `GET /api/v1/users/{id}` | Lookup user by UUID. | `UserResponse`, `200 OK`. `ROLE_ADMIN` required. |

## Pet Catalog (`pet-catalog-service`, port 8080)

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `GET /api/v1/pets` | Optional `species`, `breed`, `minPrice`, `maxPrice`, `availabilityStatus`, pageable (`page`, `size`, `sort`). | `Page<PetResponse>`, `200 OK`. Public. |
| `GET /api/v1/pets/{petId}` | `petId` is UUID. | `PetResponse`, `200 OK`; `404` if missing. Public. |
| `POST /api/v1/pets` | JSON: `name`, `species`, `breed`, `age`, `gender`, `price`, `description`. Emits `PetCreatedEvent`. | `PetResponse`, `201 Created`. `ROLE_ADMIN` required. |
| `PUT /api/v1/pets/{petId}` | Full pet fields including `availabilityStatus`. | `PetResponse`, `200 OK`. `ROLE_ADMIN` required. |
| `DELETE /api/v1/pets/{petId}` | Hard delete by UUID. | `204 No Content`. `ROLE_ADMIN` required. |

## Orders (`order-service`, port 8081)

All `/api/v1/orders/**` endpoints require `ROLE_CUSTOMER` or `ROLE_ADMIN` Bearer JWT. Customer identity (`Long customerId`) is derived deterministically from the authenticated JWT principal.

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `POST /api/v1/orders` | `{ "items": [{ "petId": "UUID", "quantity": 1 }] }`. Validates catalog pets, saves order (`CREATED`), and publishes `OrderCreatedEvent`. | `OrderResponse`, `201 Created`. `CUSTOMER` or `ADMIN`. |
| `GET /api/v1/orders/{orderId}` | Numeric order ID. Customers may only access their own orders; Admins may access any order. | `OrderResponse`, `200 OK`. `CUSTOMER` or `ADMIN`. |
| `GET /api/v1/orders` | Optional `customerId`, `status`, pageable (`page`, `size`, `sort`). Customers are scoped to their own `customerId`. | `Page<OrderResponse>`, `200 OK`. `CUSTOMER` or `ADMIN`. |
| `PATCH /api/v1/orders/{orderId}/cancel` | Cancels an order in `CREATED` or `PAYMENT_PENDING` state and emits `OrderCancelledEvent`. | `OrderResponse`, `200 OK`; `409 Conflict` if already paid/confirmed. |

## Inventory (`inventory-service`, port 8082)

All `/api/v1/inventory/**` endpoints require `ROLE_ADMIN` Bearer JWT.

| Method/path | Request and behavior | Response/status |
|---|---|---|
| `GET /api/v1/inventory/pets/{petId}` | Returns inventory status for UUID `petId`. | `InventoryResponse`, `200 OK`. |
| `POST /api/v1/inventory/pets/{petId}/reserve` | Manually reserves pet. | `InventoryResponse`, `200 OK`. |
| `POST /api/v1/inventory/pets/{petId}/release` | Releases reservation and syncs catalog. | `InventoryResponse`, `200 OK`. |
| `POST /api/v1/inventory/pets/{petId}/sold` | Marks pet sold. | `InventoryResponse`, `200 OK`. |

## Payment (`payment-service`, port 8083)

| Method/path | Purpose | Response/status |
|---|---|---|
| `GET /api/v1/payments/order/{orderId}` | Lookup payment record by numeric `orderId`. | `Payment` entity JSON, `200 OK`; `404` if absent. |

## Notifications (`notification-service`, port 8084)

| Method/path | Purpose | Response/status |
|---|---|---|
| `GET /api/v1/notifications/order/{orderId}` | Lookup notification by numeric `orderId`. | `Notification`, `200 OK`; `404` if absent. |
| `GET /api/v1/notifications/customer/{customerId}` | List notifications for numeric `customerId`. | `List<Notification>`, `200 OK`. |

## Shipping (`shipping-service`, port 8085)

| Method/path | Purpose | Response/status |
|---|---|---|
| `GET /api/v1/shipments/order/{orderId}` | Lookup shipment by numeric `orderId`. | `Shipment`, `200 OK`; `404` if absent. |
| `GET /api/v1/shipments/customer/{customerId}` | List shipments for numeric `customerId`. | `List<Shipment>`, `200 OK`. |
| `PUT /api/v1/shipments/order/{orderId}/delivered` | Mark shipment `DELIVERED`. | `Shipment`, `200 OK`. |

## Delivery Tracking (`delivery-tracking-service`, port 8086) & Fulfillment (`fulfillment-service`, port 8087)

- `GET /api/v1/deliveries/order/{orderId}`, `GET /api/v1/deliveries/customer/{customerId}`, `PUT /api/v1/deliveries/order/{orderId}/delivered`
- `GET /api/v1/fulfillment/order/{orderId}`, `GET /api/v1/fulfillment/customer/{customerId}`
