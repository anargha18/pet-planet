# Delivery tracking service

**Module:** `delivery-tracking-service` · **Package root:** `com.petcommerce.delivery` · **Database:** `petplanet_delivery` · **Port:** 8086.

- Consumes `shipment-created-events`, creates an `IN_TRANSIT` delivery record for the order's `customerId`, and emits `DeliveryStatusUpdatedEvent`.
- Supports `PUT /api/v1/deliveries/order/{orderId}/delivered` to transition delivery status to `DELIVERED` and emit a follow-up `DeliveryStatusUpdatedEvent`.
- HTTP (via `DeliveryTrackingController`, returns `DeliveryResponse`): `GET /api/v1/deliveries/order/{orderId}` and `/customer/{customerId}` are owner-or-admin (checked in `DeliveryTrackingService`, 403 otherwise, 404 if none); `PUT …/delivered` is admin-only (404 if no delivery). JWT validated by the shared platform-commons resource server.
