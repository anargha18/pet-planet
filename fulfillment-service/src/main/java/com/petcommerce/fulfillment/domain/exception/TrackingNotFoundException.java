package com.petcommerce.fulfillment.domain.exception;

/** The projection has no row for the order (yet) (→ 404). */
public class TrackingNotFoundException extends RuntimeException {

    public TrackingNotFoundException(Long orderId) {
        super("No tracking information found for order " + orderId);
    }
}
