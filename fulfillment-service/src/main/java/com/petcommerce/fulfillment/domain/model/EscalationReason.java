package com.petcommerce.fulfillment.domain.model;

/** Why an order needs a human (R-SVC-01). */
public enum EscalationReason {
    /** The order stayed CREATED/PAYMENT_PENDING longer than the configured limit. Cleared when the order closes. */
    STUCK_BEFORE_CONFIRMATION,
    /** The carrier reported DELIVERY_FAILED. */
    DELIVERY_FAILED
}
