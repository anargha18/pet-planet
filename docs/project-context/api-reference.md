# API reference

Application HTTP APIs are exposed through **`gateway-service` (`http://localhost:8089`)**. Backend service ports are private in Docker Compose.

## API Gateway (`gateway-service`, port 8089)

The gateway routes `/api/v1/auth/**` and `/api/v1/users/**` to the internal `user-service:8088`, `/api/v1/pets/**` to `pet-catalog-service:8080`, `/api/v1/orders/**` to `order-service:8081`, `/api/v1/inventory/**` to `inventory-service:8082`, `/api/v1/payments/**` to `payment-service:8083`, `/api/v1/notifications/**` to `notification-service:8084`, `/api/v1/shipments/**` to `shipping-service:8085`, `/api/v1/deliveries/**` to `delivery-tracking-service:8086`, and `/api/v1/fulfillment/**` to `fulfillment-service:8087`.

All routed requests except catalog `GET` and CORS preflight requests require a valid bearer JWT. `POST /api/v1/auth/register` and `POST /api/v1/auth/login` are public and served by user-service, which issues the token. The gateway applies only coarse rules (admin-only prefixes: user management, catalog writes, inventory, advancing a delivery); `/internal/**` is never reachable. **Every service validates the token again and enforces ownership itself**: a `USER` gets `403` for another customer's records, an `ADMIN` may read any. See [security.md](./security.md).

**Errors.** Every error response, from the gateway or any service, has the same body: `{ "status", "error", "message", "path", "timestamp", "correlationId" }`. `401` = missing/invalid/expired token, `403` = role or ownership, `404` = not found, `409` = invalid state, `400` = validation or malformed JSON. Responses carry an `X-Correlation-Id` header (the same value as `correlationId`).

## Authentication (`user-service`, via the gateway)

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `POST /api/v1/auth/register` | JSON: `email`, `password` (minimum 8 chars), `firstName`, `lastName`. Always creates a `USER`; public requests cannot select `ADMIN`. | `AuthResponse` (`token`, `tokenType`, integer `id`, `email`, `role`), `201 Created`. Public. |
| `POST /api/v1/auth/login` | JSON: `email`, `password` (both required). user-service verifies the BCrypt credentials and issues the RS256 JWT. | `AuthResponse`, `200 OK`; `401` for invalid credentials. Public. |

## User management (`user-service`, internal port 8088)

Self-service (`/me`) endpoints take the user ID from the verified token; user administration requires `ADMIN` (checked at the gateway and again in user-service). User-service endpoints also require the gateway/user-service internal token, which the gateway adds.

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
| `GET /api/v1/pets` | Optional `species`, `breed`, `minPrice`, `maxPrice`, `availabilityStatus`, pageable (`page`, `size`, `sort`). Archived (`RETIRED`) pets are excluded unless `availabilityStatus=RETIRED` is requested. | `Page<PetResponse>`, `200 OK`. Public. |
| `GET /api/v1/pets/{petId}` | `petId` is UUID. | `PetResponse`, `200 OK`; `404` if missing. Public. |
| `POST /api/v1/pets` | JSON: `name`, `species`, `breed`, `age`, `gender`, `price`, `description`. Emits `PetCreatedEvent`. | `PetResponse`, `201 Created`. `ADMIN`. |
| `PUT /api/v1/pets/{petId}` | `name`, `species`, `breed`, `age`, `gender`, `price`, `description`. `availabilityStatus` is **not** accepted: inventory owns availability. | `PetResponse`, `200 OK`. `ADMIN`. |
| `DELETE /api/v1/pets/{petId}` | **Archives** (soft delete): status becomes `RETIRED`, the pet is hidden from the default listing, can no longer be ordered, and inventory retires it. Repeating is a no-op. | `204 No Content`; `409 Conflict` if the pet is `RESERVED` or `SOLD`. `ADMIN`. |

## Orders (`order-service`, port 8081)

`customerId` is the integer user ID from the signed token, stored in the order as a `Long`. `USER` callers are limited to their own orders; `ADMIN` callers may inspect any order.

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `POST /api/v1/orders` | `{ "items": [{ "petId": "UUID", "quantity": 1 }] }`. Validates catalog pets, saves the order (`CREATED`), and publishes `OrderCreatedEvent`. | `OrderResponse`, `201 Created`. `USER` or `ADMIN`. |
| `GET /api/v1/orders/{orderId}` | Numeric order ID. | `OrderResponse`, `200 OK`. `USER` or `ADMIN`, owner-scoped for `USER`. |
| `GET /api/v1/orders` | Optional `customerId`, `status`, pageable (`page`, `size`, `sort`). `USER` callers are scoped to their own `customerId`. | `Page<OrderResponse>`, `200 OK`. `USER` or `ADMIN`. |
| `PATCH /api/v1/orders/{orderId}/cancel` | Cancels an order in `CREATED` or `PAYMENT_PENDING` state and emits `OrderCancelledEvent`. | `OrderResponse`, `200 OK`; `409 Conflict` if already paid/confirmed. |

## Inventory (`inventory-service`, port 8082)

All `/api/v1/inventory/**` endpoints require `ADMIN`. Inventory is **read-only** over HTTP (R-SAGA-07): reservations, releases and sales happen only through the checkout saga.

| Method/path | Purpose | Response/status |
|---|---|---|
| `GET /api/v1/inventory/pets` | Admin overview (Phase 5): optional `status` (`AVAILABLE`, `RESERVED`, `SOLD`, `RETIRED`), pageable (`page`, `size` default 20, sort default `updatedAt,desc`). | `Page<InventoryResponse>`, `200 OK`. |
| `GET /api/v1/inventory/pets/{petId}` | Returns inventory status for UUID `petId`. | `InventoryResponse`, `200 OK`; `404` if inventory has no record of the pet. |

`InventoryResponse`: `petId`, `status`, `orderId` (reserving or buying order, kept after the sale), `reservationId`, `reservedAt`, `reservationExpiresAt`, `soldAt`, `version`, `updatedAt` (`orderId` and `updatedAt` added in Phase 5).

## Payments, notifications, shipping, delivery, and fulfillment

All require a valid bearer token (`USER` or `ADMIN`). Each service checks ownership itself: `…/order/{orderId}` returns the record only if the caller owns the order (or is an admin), otherwise `403`; `404` if there is no record. `…/customer/{customerId}` is allowed only for the caller's own user ID (or an admin), otherwise `403`. Responses are DTOs (never JPA entities).

| Method/path | Response (`200 OK`) | Access |
|---|---|---|
| `GET /api/v1/payments/order/{orderId}` | `PaymentResponse`: `paymentId`, `orderId`, `customerId`, `amount`, `currency`, `status` (`PROCESSING`/`SUCCEEDED`/`FAILED`/`REFUNDED`), `transactionReference`, `createdAt`, `updatedAt`, `refundedAt`, `refundReason` | owner or `ADMIN`; payments created before payment `V4` have `customerId` `null` and are visible to `ADMIN` only |
| `GET /api/v1/notifications/order/{orderId}` | `NotificationResponse[]` (oldest first): `id`, `orderId`, `customerId`, `type` (`ORDER_CONFIRMED`, `ORDER_CANCELLED`, `ORDER_FAILED`, `PAYMENT_REFUNDED`, `SHIPPED`, `OUT_FOR_DELIVERY`, `DELIVERED`, `DELIVERY_FAILED`), `message`, `status`, `createdAt`, `sentAt`; `[]` if none yet | owner or `ADMIN` |
| `GET /api/v1/notifications/customer/{customerId}` | `NotificationResponse[]` (newest first) | self or `ADMIN` |
| `GET /api/v1/notifications/me` | `NotificationResponse[]` of the caller | any signed-in user |
| `GET /api/v1/shipments/order/{orderId}` | `ShipmentResponse`: `id`, `orderId`, `customerId`, `status` (`CREATED`, `SHIPPED`, `DELIVERED`, `DELIVERY_FAILED`; mirrored from delivery-tracking), `trackingNumber`, `createdAt`, `shippedAt`, `deliveredAt` | owner or `ADMIN` |
| `GET /api/v1/shipments/customer/{customerId}` | `ShipmentResponse[]` | self or `ADMIN` |
| `GET /api/v1/deliveries/order/{orderId}` | `DeliveryResponse`: `id`, `orderId`, `customerId`, `trackingNumber`, `status` (`IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `DELIVERY_FAILED`), `createdAt`, `updatedAt`, `deliveredAt`, `timeline[]` (`status`, `note`, `occurredAt`) | owner or `ADMIN` |
| `GET /api/v1/deliveries/customer/{customerId}` | `DeliveryResponse[]` | self or `ADMIN` |
| `PUT /api/v1/deliveries/order/{orderId}/status` | Body `{ "status": "OUT_FOR_DELIVERY" \| "DELIVERED" \| "DELIVERY_FAILED", "note"?: string ≤ 500 }` → `DeliveryResponse`; `404` unknown order, `409` illegal transition, `400` missing/unknown status | `ADMIN` |
| `GET /api/v1/fulfillment/orders/{orderId}` | `TrackingResponse`: `summary` (`orderId`, `customerId`, `orderStatus`, `paymentStatus`, `shipmentStatus`, `trackingNumber`, `deliveryStatus`, `totalAmount`, `escalated`, `escalationReason`, `createdAt`, `updatedAt`; statuses `null` until seen) and `timeline[]` (`eventType`, `source`, `description`, `occurredAt`, oldest first); `404` if the projection has not seen the order | owner or `ADMIN` |
| `GET /api/v1/fulfillment/orders/me` | Page of `TrackingSummaryResponse` (`page`, `size` default 20, sorted by `updatedAt` desc) | any signed-in user |
| `GET /api/v1/fulfillment/orders?customerId=&escalated=` | Page of `TrackingSummaryResponse`, both filters optional | `ADMIN` |

There is one delivery action: `PUT /api/v1/deliveries/order/{orderId}/status` (Phase 4, R-SAGA-06). Shipping mirrors it from events, and its former `PUT …/delivered` (and delivery-tracking's) were removed.
