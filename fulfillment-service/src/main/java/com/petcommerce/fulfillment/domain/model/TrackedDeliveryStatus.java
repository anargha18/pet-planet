package com.petcommerce.fulfillment.domain.model;

/** Mirrors delivery-tracking: IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED / DELIVERY_FAILED. */
public enum TrackedDeliveryStatus {
    IN_TRANSIT(0),
    OUT_FOR_DELIVERY(1),
    DELIVERED(2),
    DELIVERY_FAILED(2);

    private final int rank;

    TrackedDeliveryStatus(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }

    /** Parses delivery-tracking's status text (legacy {@code FAILED} = DELIVERY_FAILED); null if unknown. */
    public static TrackedDeliveryStatus parse(String value) {
        if (value == null) {
            return null;
        }
        if ("FAILED".equals(value)) {
            return DELIVERY_FAILED;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public TrackedShipmentStatus shipmentStatus() {
        return switch (this) {
            case IN_TRANSIT, OUT_FOR_DELIVERY -> TrackedShipmentStatus.SHIPPED;
            case DELIVERED -> TrackedShipmentStatus.DELIVERED;
            case DELIVERY_FAILED -> TrackedShipmentStatus.DELIVERY_FAILED;
        };
    }
}
