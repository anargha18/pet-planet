package com.petcommerce.fulfillment.domain.model;

/** The projection's copy of order-service's order status. Ranks keep it monotonic when events arrive out of order. */
public enum TrackedOrderStatus {
    CREATED(0),
    PAYMENT_PENDING(1),
    CONFIRMED(2),
    CANCELLED(2),
    FAILED(2);

    private final int rank;

    TrackedOrderStatus(int rank) {
        this.rank = rank;
    }

    public boolean isTerminal() {
        return rank == 2;
    }

    /** Only forward moves; a terminal status never changes. */
    public boolean canBeReplacedBy(TrackedOrderStatus next) {
        return next != null && !isTerminal() && next.rank > rank;
    }
}
