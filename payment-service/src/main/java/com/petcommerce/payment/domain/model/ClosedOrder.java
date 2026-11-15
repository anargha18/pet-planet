package com.petcommerce.payment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Marker that an order was cancelled or failed. A PetReserved event processed after this point must never
 * charge the customer (R-SAGA-04).
 */
@Entity
@Table(name = "closed_orders")
public class ClosedOrder {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    protected ClosedOrder() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getClosedAt() {
        return closedAt;
    }
}
