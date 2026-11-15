package com.petcommerce.payment.domain.model;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    /** A successful charge was returned because the order was cancelled or failed. */
    REFUNDED
}
