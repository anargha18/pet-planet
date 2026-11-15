package com.petcommerce.fulfillment.domain.model;

/** The projection's view of the payment: PENDING once the charge starts, then its outcome; REFUNDED is final. */
public enum TrackedPaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED
}
