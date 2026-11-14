package com.petcommerce.shipping.domain.exception;

/** No shipment exists for the order (→ 404). */
public class ShipmentNotFoundException extends RuntimeException {

    public ShipmentNotFoundException(Long orderId) {
        super("No shipment found for order " + orderId);
    }
}
