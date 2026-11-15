package com.petcommerce.shipping.domain.model;

/**
 * Shipment lifecycle (R-SVC-03): {@code CREATED → SHIPPED → DELIVERED / DELIVERY_FAILED}. Only creation happens here;
 * the later statuses mirror delivery-tracking's {@code DeliveryStatusUpdatedEvent}s (shipping has no mutation API).
 */
public enum ShipmentStatus {
    CREATED,
    SHIPPED,
    DELIVERED,
    DELIVERY_FAILED;

    public boolean isTerminal() {
        return this == DELIVERED || this == DELIVERY_FAILED;
    }

    /**
     * The shipment status that mirrors a delivery status, or null if it means nothing for the shipment:
     * {@code IN_TRANSIT}/{@code OUT_FOR_DELIVERY} → SHIPPED, {@code DELIVERED} → DELIVERED,
     * {@code DELIVERY_FAILED} (or legacy {@code FAILED}) → DELIVERY_FAILED.
     */
    public static ShipmentStatus mirroring(String deliveryStatus) {
        if (deliveryStatus == null) {
            return null;
        }
        return switch (deliveryStatus) {
            case "IN_TRANSIT", "OUT_FOR_DELIVERY" -> SHIPPED;
            case "DELIVERED" -> DELIVERED;
            case "DELIVERY_FAILED", "FAILED" -> DELIVERY_FAILED;
            default -> null;
        };
    }
}
