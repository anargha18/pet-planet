package com.petcommerce.delivery.domain.exception;

import com.petcommerce.delivery.domain.model.DeliveryStatus;

/** The requested delivery status is not reachable from the current one (→ 409). */
public class InvalidDeliveryTransitionException extends RuntimeException {

    public InvalidDeliveryTransitionException(Long orderId, DeliveryStatus from, DeliveryStatus to) {
        super("Delivery of order " + orderId + " cannot move from " + from + " to " + to
                + " (allowed: " + from.allowedNext() + ")");
    }
}
