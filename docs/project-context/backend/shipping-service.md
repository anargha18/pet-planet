# Shipping service

**Module:** `shipping-service` · **Package root:** `com.petcommerce.shipping` · **Database:** `petplanet_shipping` · **Port:** 8085.

Creates shipments and **mirrors** the carrier's progress (R-SVC-03). It has no mutation API.

- Consumes `order-confirmed-events`: creates a `CREATED` shipment with tracking number `PET-{orderId}-…` and the order's `customerId`, then emits `ShipmentCreatedEvent`.
- Consumes `delivery-status-updated-events` (Phase 4): `IN_TRANSIT`/`OUT_FOR_DELIVERY` → `SHIPPED` (sets `shippedAt`), `DELIVERED` → `DELIVERED` (sets `deliveredAt`), `DELIVERY_FAILED` → `DELIVERY_FAILED`. The handler is idempotent (`processed_events`) and monotonic (`Shipment.mirrorDelivery`): a terminal shipment never changes. An event for an order without a shipment is logged and skipped.
- Statuses: `CREATED → SHIPPED → DELIVERED / DELIVERY_FAILED` (`V3` added a check constraint).
- HTTP (read-only, `ShipmentResponse` DTOs): `GET /api/v1/shipments/order/{orderId}` and `GET /api/v1/shipments/customer/{customerId}`. Both are owner or admin (checked in `ShippingService`; 403 otherwise, 404 if none). Everything else is denied. The former `PUT …/delivered` was removed in Phase 4. See [security.md](../security.md).
