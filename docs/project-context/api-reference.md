# API reference

Application HTTP APIs are exposed through **`gateway-service` (`http://localhost:8089`)**. Backend service ports are private in Docker Compose.

## API Gateway (`gateway-service`, port 8089)

The gateway routes `/api/v1/auth/**` and `/api/v1/users/**` to the internal `user-service:8088`, `/api/v1/pets/**` to `pet-catalog-service:8080`, `/api/v1/orders/**` to `order-service:8081`, `/api/v1/inventory/**` to `inventory-service:8082`, `/api/v1/payments/**` to `payment-service:8083`, `/api/v1/notifications/**` to `notification-service:8084`, `/api/v1/shipments/**` to `shipping-service:8085`, `/api/v1/deliveries/**` to `delivery-tracking-service:8086`, and `/api/v1/fulfillment/**` to `fulfillment-service:8087`.

All routed requests except catalog `GET` and CORS preflight requests require a valid bearer JWT. `POST /api/v1/auth/register` and `POST /api/v1/auth/login` are public and served by user-service, which issues the token. Catalog writes and inventory routes require `ADMIN`; order routes require `USER` or `ADMIN`; user management endpoints have the rules listed below. Customer-specific lists and tracking lookups are owner-scoped at the gateway; domain services retain relevant token and ownership checks as defense in depth.

## Authentication (`user-service`, via the gateway)

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `POST /api/v1/auth/register` | JSON: `email`, `password` (minimum 8 chars), `firstName`, `lastName`. Always creates a `USER`; public requests cannot select `ADMIN`. | `AuthResponse` (`token`, `tokenType`, integer `id`, `email`, `role`), `201 Created`. Public. |
| `POST /api/v1/auth/login` | JSON: `email`, `password` (both required). user-service verifies the BCrypt credentials and issues the RS256 JWT. | `AuthResponse`, `200 OK`; `401` for invalid credentials. Public. |

## User management (`user-service`, internal port 8088)

The gateway authorizes these operations and passes a trusted authenticated user ID for self-service endpoints. User-service endpoints require the gateway/user-service internal token.

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `GET /api/v1/users/me` | Current active profile. | `UserResponse` (`id` integer, `email`, `firstName`, `lastName`, `role`, `active`, `createdAt`, `updatedAt`), `200 OK`. `USER` or `ADMIN`. |
| `PATCH /api/v1/users/me` | JSON: `email`, `firstName`, `lastName`; updates only the caller's profile. | `UserResponse`, `200 OK`. `USER` or `ADMIN`. |
| `PUT /api/v1/users/me/password` | JSON: `currentPassword`, `newPassword` (minimum 8 chars). | `204 No Content`; `401` if current password is incorrect. `USER` or `ADMIN`. |
| `GET /api/v1/users` | Pageable list (`page`, `size`, `sort`); includes active and inactive users. | `Page<UserResponse>`, `200 OK`. `ADMIN`. |
| `POST /api/v1/users` | JSON: `email`, `password`, `firstName`, `lastName`, `role`. | `UserResponse`, `201 Created`. `ADMIN`. |
| `GET /api/v1/users/{id}` | Lookup by generated integer ID. | `UserResponse`, `200 OK`; `404` if absent. `ADMIN`. |
| `PATCH /api/v1/users/{id}` | Optional `email`, `firstName`, `lastName`, `role`, `active` fields. | `UserResponse`, `200 OK`; `409` if this would demote or deactivate the last active admin. `ADMIN`. |
| `DELETE /api/v1/users/{id}` | Soft-deactivates the account; the row and email remain. | `204 No Content`; `409` if this would deactivate the last active admin. `ADMIN`. |

## Pet Catalog (`pet-catalog-service`, port 8080)

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `GET /api/v1/pets` | Optional `species`, `breed`, `minPrice`, `maxPrice`, `availabilityStatus`, pageable (`page`, `size`, `sort`). | `Page<PetResponse>`, `200 OK`. Public. |
| `GET /api/v1/pets/{petId}` | `petId` is UUID. | `PetResponse`, `200 OK`; `404` if missing. Public. |
| `POST /api/v1/pets` | JSON: `name`, `species`, `breed`, `age`, `gender`, `price`, `description`. Emits `PetCreatedEvent`. | `PetResponse`, `201 Created`. `ADMIN`. |
| `PUT /api/v1/pets/{petId}` | Full pet fields including `availabilityStatus`. | `PetResponse`, `200 OK`. `ADMIN`. |
| `DELETE /api/v1/pets/{petId}` | Hard delete by UUID. | `204 No Content`. `ADMIN`. |

## Orders (`order-service`, port 8081)

`customerId` is the integer user ID from the signed token, stored in the order as a `Long`. `USER` callers are limited to their own orders; `ADMIN` callers may inspect any order.

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `POST /api/v1/orders` | `{ "items": [{ "petId": "UUID", "quantity": 1 }] }`. Validates catalog pets, saves the order (`CREATED`), and publishes `OrderCreatedEvent`. | `OrderResponse`, `201 Created`. `USER` or `ADMIN`. |
| `GET /api/v1/orders/{orderId}` | Numeric order ID. | `OrderResponse`, `200 OK`. `USER` or `ADMIN`, owner-scoped for `USER`. |
| `GET /api/v1/orders` | Optional `customerId`, `status`, pageable (`page`, `size`, `sort`). `USER` callers are scoped to their own `customerId`. | `Page<OrderResponse>`, `200 OK`. `USER` or `ADMIN`. |
| `PATCH /api/v1/orders/{orderId}/cancel` | Cancels an order in `CREATED` or `PAYMENT_PENDING` state and emits `OrderCancelledEvent`. | `OrderResponse`, `200 OK`; `409 Conflict` if already paid/confirmed. |

## Inventory (`inventory-service`, port 8082)

All `/api/v1/inventory/**` endpoints require `ADMIN`.

| Method/path | Purpose | Response/status |
|---|---|---|
| `GET /api/v1/inventory/pets/{petId}` | Returns inventory status for UUID `petId`. | `InventoryResponse`, `200 OK`. |
| `POST /api/v1/inventory/pets/{petId}/reserve` | Manually reserves a pet. | `InventoryResponse`, `200 OK`. |
| `POST /api/v1/inventory/pets/{petId}/release` | Releases reservation and syncs catalog. | `InventoryResponse`, `200 OK`. |
| `POST /api/v1/inventory/pets/{petId}/sold` | Marks pet sold. | `InventoryResponse`, `200 OK`. |

## Payments, notifications, shipping, delivery, and fulfillment

These APIs require a valid gateway bearer token. Existing service-level ownership checks apply where implemented.

- `GET /api/v1/payments/order/{orderId}` — payment record by numeric order ID.
- `GET /api/v1/notifications/order/{orderId}`, `GET /api/v1/notifications/customer/{customerId}`.
- `GET /api/v1/shipments/order/{orderId}`, `GET /api/v1/shipments/customer/{customerId}`, `PUT /api/v1/shipments/order/{orderId}/delivered`.
- `GET /api/v1/deliveries/order/{orderId}`, `GET /api/v1/deliveries/customer/{customerId}`, `PUT /api/v1/deliveries/order/{orderId}/delivered`.
- `GET /api/v1/fulfillment/order/{orderId}`, `GET /api/v1/fulfillment/customer/{customerId}`.
