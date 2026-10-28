# API reference

This reference reflects controller mappings and service security configuration inspected in source. User-service explicitly configures access rules; no authentication configuration was found in the other services. This describes repository configuration, not a deployment boundary. For unhandled exceptions, framework responses are not treated as a stable contract.

## Pet catalog (`pet-catalog-service`, default port 8080)

Base path `/api/v1/pets`.

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `POST /api/v1/pets` | JSON: `name`, `species` (`CAT`/`DOG`), `breed`, `age`, `gender` (`MALE`/`FEMALE`), `price`, `description`. Requires nonblank text fields, non-null enums, age >= 0, price >= 0.01. | `PetResponse`, `201 Created`; validation `400`; no auth configured. |
| `GET /api/v1/pets/{petId}` | `petId` is UUID. | `PetResponse`, `200`; missing pet `404`. |
| `GET /api/v1/pets` | Optional `species`, `breed`, `minPrice`, `maxPrice`, `availabilityStatus`; Spring pageable `page`, `size`, `sort`; default size 10, `createdAt` descending. | `Page<PetResponse>`, `200`; no auth configured. |
| `PUT /api/v1/pets/{petId}` | Full pet fields including required `availabilityStatus`; same validation as create. | `PetResponse`, `200`; missing `404`; validation `400`. |
| `DELETE /api/v1/pets/{petId}` | Hard delete by UUID. | Empty body, `204`; missing `404`. |

`PetResponse`: `id`, `name`, `species`, `breed`, `age`, `gender`, `price`, `description`, `availabilityStatus`, `createdAt`, `updatedAt`. It has no image field.

## Orders (`order-service`, default port 8081)

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `POST /api/v1/orders` | `{ "customerId": 123, "items": [{ "petId": "UUID", "quantity": 1 }] }`. Customer ID required; items nonempty; pet UUID required; request quantity >= 1 and service requires exactly 1. Catalog is queried synchronously and availability checked. | `OrderResponse`, `201`; invalid `400`, missing pet/order `404`, unavailable pet/invalid state `409`, catalog unavailable `503`. No auth configured. |
| `GET /api/v1/orders/{orderId}` | Numeric ID. | `OrderResponse`, `200`; missing `404`. |
| `GET /api/v1/orders` | Optional `customerId`, `status`; pageable `page`, `size`, `sort`. Default size 10, `createdAt` descending; sort limited to `id`, `customerId`, `status`, `createdAt`, `updatedAt`. | `Page<OrderResponse>`, `200`; unsupported sort maps to `400`. |
| `PATCH /api/v1/orders/{orderId}/cancel` | No body. Only `CREATED` may be cancelled. | `OrderResponse`, `200`; missing `404`; invalid state `409`. |

`OrderResponse`: `id`, `customerId`, `status`, `totalAmount`, `items`, `createdAt`, `updatedAt`. Each `OrderItemResponse` contains `id`, `petId`, `petName`, `petSpecies`, `petBreed`, `unitPrice`, `quantity`, `subtotal`. No payment initiation endpoint.

## Inventory (`inventory-service`, default port 8082)

Base path `/api/v1/inventory`; no auth configured.

| Method/path | Request and behavior | Response/status |
|---|---|---|
| `GET /api/v1/inventory/pets/{petId}` | UUID; missing record is represented as AVAILABLE without persistence. | `InventoryResponse`, implicit `200`. |
| `POST /api/v1/inventory/pets/{petId}/reserve` | UUID; server generates reservation ID. | `InventoryResponse`, implicit `200`. |
| `POST /api/v1/inventory/pets/{petId}/release` | UUID; release existing reservation. | `InventoryResponse`, implicit `200`. |
| `POST /api/v1/inventory/pets/{petId}/sold` | UUID; mark existing inventory sold. | `InventoryResponse`, implicit `200`. |

`InventoryResponse`: `petId`, `status`, `reservationId`, `reservedAt`, `reservationExpiresAt`, `soldAt`, `version`. No inventory-specific controller advice; exception status behavior is not a documented contract.

## Payment (`payment-service`, default port 8083)

No REST controller found. Consumes `pet-reserved-events`; `PAYMENT_SIMULATION_RESULT` controls result. There is no HTTP request/response contract.

## Notifications (`notification-service`, default port 8084)

Base path `/api/v1`; no auth configured.

| Method/path | Purpose | Response/status |
|---|---|---|
| `GET /api/v1/notifications/order/{orderId}` | Lookup by numeric order ID. | Notification entity, `200`; `404` if absent. |
| `GET /api/v1/notifications/customer/{customerId}` | Lookup by numeric customer ID. | List of notification entities, `200` (possibly empty). |

Entity JSON fields: `id`, `orderId`, `customerId`, `message`, `status`, `createdAt`, `sentAt`.

## Shipping (`shipping-service`, default port 8085)

Base path `/api/v1`; no auth configured.

| Method/path | Purpose | Response/status |
|---|---|---|
| `GET /api/v1/shipments/order/{orderId}` | Lookup by numeric order ID. | Shipment entity, `200`; `404` if absent. |
| `GET /api/v1/shipments/customer/{customerId}` | List by numeric customer ID. | List of shipment entities, `200`. |

Entity fields: `id`, `orderId`, `customerId`, `status`, `trackingNumber`, `createdAt`, `shippedAt`, `deliveredAt`.

## Delivery tracking (`delivery-tracking-service`, default port 8086)

Base path `/api/v1`; no auth configured.

| Method/path | Purpose | Response/status |
|---|---|---|
| `GET /api/v1/deliveries/order/{orderId}` | Lookup by numeric order ID. | Delivery record, `200`; `404` if absent. |
| `GET /api/v1/deliveries/customer/{customerId}` | List by numeric customer ID. | List of delivery records, `200`. |
| `PUT /api/v1/deliveries/order/{orderId}/delivered` | Mark delivered and emit status event; no body. | Delivery record, `200`; missing-record exception has no specific controller mapping. |

Record fields: `id`, `orderId`, `customerId`, `trackingNumber`, `status`, `createdAt`, `updatedAt`, `deliveredAt`.

## Fulfillment (`fulfillment-service`, default port 8087)

Base path `/api/v1`; no auth configured.

| Method/path | Purpose | Response/status |
|---|---|---|
| `GET /api/v1/fulfillment/order/{orderId}` | Lookup by numeric order ID. | Fulfillment record, `200`; `404` if absent. |
| `GET /api/v1/fulfillment/customer/{customerId}` | List by numeric customer ID. | List of fulfillment records, `200`. |

Record fields: `id`, `orderId`, `customerId`, `trackingNumber`, `status`, `lastUpdatedAt`, `notes`.

## User/authentication (`user-service`, default port 8088)

All endpoints are under `/api/v1`. Auth routes are public; profile requires valid bearer JWT; user lookup requires ADMIN. Security rules are detailed in [security.md](./security.md).

| Method/path | Request and behavior | Response/status/access |
|---|---|---|
| `POST /api/v1/auth/register` | JSON: `email`, `password`, `firstName`, `lastName`; email validated, other fields nonblank. | `AuthResponse`, `201`; duplicate-email argument exception maps to `400`. Public. |
| `POST /api/v1/auth/login` | JSON: `email`, `password`; email and nonblank password validation. | `AuthResponse`, `200`; current advice maps argument/authentication failures to `400`. Public. |
| `GET /api/v1/users/me` | Current authenticated profile. | `UserResponse`; bearer JWT required. |
| `GET /api/v1/users/{id}` | `id` is UUID. | `UserResponse`; `ROLE_ADMIN` required. |

`AuthResponse`: `token`, `tokenType`, `email`, `role`. `UserResponse`: `id` (UUID), `email`, `firstName`, `lastName`, `role`.

## Operational/docs paths

Backend configs expose Actuator `health` and `info`; health probe support is enabled. Springdoc API docs path is `/api-docs`; Swagger UI path is `/swagger-ui.html`. User-service explicitly permits docs and health; other services have no visible security configuration.

