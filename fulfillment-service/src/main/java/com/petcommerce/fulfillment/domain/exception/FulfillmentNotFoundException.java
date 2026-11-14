package com.petcommerce.fulfillment.domain.exception;

/** No fulfillment record exists for the order (→ 404). */
public class FulfillmentNotFoundException extends RuntimeException {

    public FulfillmentNotFoundException(Long orderId) {
        super("No fulfillment record found for order " + orderId);
    }
}
