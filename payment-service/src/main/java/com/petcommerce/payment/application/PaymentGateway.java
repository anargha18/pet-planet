package com.petcommerce.payment.application;

import java.math.BigDecimal;

/**
 * Port to whatever actually moves money. The only implementation today is the simulated gateway in
 * {@code client}; business logic in {@link PaymentService} never knows which one it talks to.
 */
public interface PaymentGateway {

    ChargeResult charge(Long orderId, BigDecimal amount);

    /** Refunds are simulated as always succeeding. */
    String refund(Long orderId, BigDecimal amount, String transactionReference);

    record ChargeResult(boolean success, String transactionReference, String failureReason) {
    }
}
