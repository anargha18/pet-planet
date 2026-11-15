package com.petcommerce.fulfillment.domain.model;

/** Mirrors shipping-service: CREATED → SHIPPED → DELIVERED / DELIVERY_FAILED. */
public enum TrackedShipmentStatus {
    CREATED(0),
    SHIPPED(1),
    DELIVERED(2),
    DELIVERY_FAILED(2);

    private final int rank;

    TrackedShipmentStatus(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
