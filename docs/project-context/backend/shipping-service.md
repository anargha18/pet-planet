# Shipping service

**Module:** `shipping-service` · **Package root:** `com.petcommerce.shipping` · **Database:** `petplanet_shipping` · **Port:** 8085.

- Consumes `order-confirmed-events`, creates a `SHIPPED` shipment record with tracking number `PET-{orderId}-...` and the order's `customerId`, and emits `ShipmentCreatedEvent`.
- Exposes `GET /api/v1/shipments/order/{orderId}`, `GET /api/v1/shipments/customer/{customerId}`, and `PUT /api/v1/shipments/order/{orderId}/delivered`.
- Responses are `ShipmentResponse` DTOs. JWT validated by the shared platform-commons resource server; reads are owner-or-admin (checked in `ShippingService`, 403 otherwise, 404 if none); `…/delivered` is admin-only (404 if no shipment). See [security.md](../security.md).
