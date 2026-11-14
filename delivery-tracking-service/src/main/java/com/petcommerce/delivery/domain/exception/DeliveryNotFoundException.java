package com.petcommerce.delivery.domain.exception;

/** No delivery exists for the order (→ 404). */
public class DeliveryNotFoundException extends RuntimeException {

    public DeliveryNotFoundException(Long orderId) {
        super("No delivery found for order " + orderId);
    }
}
