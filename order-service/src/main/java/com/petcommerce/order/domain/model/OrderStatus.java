package com.petcommerce.order.domain.model;

/**
 * Commercial order lifecycle (R-SAGA-01). Allowed transitions live in
 * {@link com.petcommerce.order.domain.policy.OrderStateMachine}.
 * Shipping and delivery progress is not order state; it belongs to the tracking projection.
 */
public enum OrderStatus {
    /** Order saved; pets not yet reserved. */
    CREATED,
    /** All pets reserved; waiting for payment and the sale to complete. */
    PAYMENT_PENDING,
    /** Paid and every pet marked SOLD by inventory. Terminal. */
    CONFIRMED,
    /** Cancelled by the customer or because the reservation expired. Terminal. */
    CANCELLED,
    /** Reservation failed, payment failed, or the sale conflicted. Terminal. */
    FAILED
}
