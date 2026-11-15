package com.petcommerce.shipping.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShipmentStatus status;

    @Column(name = "tracking_number", nullable = false, unique = true)
    private String trackingNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected Shipment() {
    }

    /** A new shipment is {@link ShipmentStatus#CREATED}; it becomes SHIPPED once the carrier reports progress. */
    public Shipment(Long orderId, Long customerId, String trackingNumber) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = ShipmentStatus.CREATED;
        this.trackingNumber = trackingNumber;
        this.createdAt = Instant.now();
    }

    /**
     * Mirrors a delivery status reported by delivery-tracking. Never leaves a terminal status and never moves back
     * (DELIVERED/DELIVERY_FAILED stay; SHIPPED is not reset to CREATED). Returns whether anything changed.
     */
    public boolean mirrorDelivery(ShipmentStatus next, Instant at) {
        if (next == null || status.isTerminal() || next == status || next == ShipmentStatus.CREATED) {
            return false;
        }
        if (shippedAt == null) {
            shippedAt = at;
        }
        if (next == ShipmentStatus.DELIVERED) {
            deliveredAt = at;
        }
        status = next;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

}
