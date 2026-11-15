package com.petcommerce.delivery.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Carrier progress of one delivery (R-SAGA-06). Allowed moves:
 * <pre>
 * IN_TRANSIT ──▶ OUT_FOR_DELIVERY ──▶ DELIVERED
 *     │                 │
 *     └────────┬────────┴──────────▶ DELIVERY_FAILED
 *              └──▶ DELIVERED (a stop at OUT_FOR_DELIVERY is optional)
 * </pre>
 * {@code DELIVERED} and {@code DELIVERY_FAILED} are terminal.
 */
public enum DeliveryStatus {
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED;

    public Set<DeliveryStatus> allowedNext() {
        return switch (this) {
            case IN_TRANSIT -> EnumSet.of(OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED);
            case OUT_FOR_DELIVERY -> EnumSet.of(DELIVERED, DELIVERY_FAILED);
            case DELIVERED, DELIVERY_FAILED -> EnumSet.noneOf(DeliveryStatus.class);
        };
    }

    public boolean canMoveTo(DeliveryStatus next) {
        return next != null && allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
