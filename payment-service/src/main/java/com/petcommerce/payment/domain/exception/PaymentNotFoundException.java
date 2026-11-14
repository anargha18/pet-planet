package com.petcommerce.payment.domain.exception;

/** No payment exists for the order (→ 404). */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(Long orderId) {
        super("No payment found for order " + orderId);
    }
}
