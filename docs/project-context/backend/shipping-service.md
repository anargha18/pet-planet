# Shipping service

**Module:** `shipping-service` · **Package root:** `com.petcommerce.shipping` · **Database:** `petplanet_shipping` · **Port:** 8085.

- Consumes `order-confirmed-events`, creates a `SHIPPED` shipment record with tracking number `PET-{orderId}-...` and the order's `customerId`, and emits `ShipmentCreatedEvent`.
- Exposes `GET /api/v1/shipments/order/{orderId}`, `GET /api/v1/shipments/customer/{customerId}`, and `PUT /api/v1/shipments/order/{orderId}/delivered`.
