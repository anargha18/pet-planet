# Delivery tracking service

**Module:** `delivery-tracking-service` · **Package root:** `com.petcommerce.delivery` · **Database:** `petplanet_delivery` · **Port:** 8086.

Owns **carrier progress** (R-SAGA-06, R-SVC-02). It holds the only "advance delivery" action in the system.

- Consumes `shipment-created-events`: creates an `IN_TRANSIT` delivery record for the order's `customerId`, stores the saga `correlationId`, writes the first timeline row and emits `DeliveryStatusUpdatedEvent`.
- Status rules live in `domain/model/DeliveryStatus`: `IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED / DELIVERY_FAILED`. `IN_TRANSIT` may also go straight to `DELIVERED` or `DELIVERY_FAILED`. `DELIVERED` and `DELIVERY_FAILED` are terminal. `DeliveryRecord.advanceTo` rejects any other move with `InvalidDeliveryTransitionException` (→ 409) and sets `deliveredAt` on `DELIVERED`.
- Every status reached is a row in `delivery_events` (status, optional note, time). Every change emits `DeliveryStatusUpdatedEvent` with `previousStatus` and `note`, reusing the stored saga `correlationId`.
- HTTP (`DeliveryTrackingController`, returns `DeliveryResponse` with `timeline`):
  - `GET /api/v1/deliveries/order/{orderId}` and `/customer/{customerId}`: owner or admin (checked in `DeliveryTrackingService`; 403 otherwise, 404 if none).
  - `PUT /api/v1/deliveries/order/{orderId}/status` with body `{ "status": "OUT_FOR_DELIVERY" | "DELIVERED" | "DELIVERY_FAILED", "note"?: string ≤ 500 }`: admin only. Returns 404 for an unknown order, 409 for an illegal transition, 400 for a missing or unknown status.
- The former `PUT …/delivered` endpoint was removed in Phase 4. JWT is validated by the shared platform-commons resource server.
