# Fulfillment service

**Module:** `fulfillment-service` · **Package root:** `com.petcommerce.fulfillment` · **Database:** `petplanet_fulfillment` · **Port:** 8087.

- Consumes `delivery-status-updated-events`, creates a `FulfillmentRecord` (`SHIPPED` for `IN_TRANSIT`, `DELIVERED` for `DELIVERED`), and updates the existing record when subsequent delivery status changes arrive.
- HTTP (via `FulfillmentController`, returns `FulfillmentResponse`): `GET /api/v1/fulfillment/order/{orderId}` (owner or admin; 404 if none) and `/customer/{customerId}` (self or admin), ownership checked in `FulfillmentService`. JWT validated by the shared platform-commons resource server.
