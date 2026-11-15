package com.petcommerce.delivery.domain.model;

import com.petcommerce.delivery.domain.exception.InvalidDeliveryTransitionException;
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

/** Current carrier status of one order's delivery; every change is also recorded as a {@link DeliveryEvent}. */
@Entity
@Table(name = "delivery_records")
public class DeliveryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "tracking_number", nullable = false)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeliveryStatus status;

    /** Saga correlation ID taken from ShipmentCreated, reused for the admin's later status events (R-EVT-08). */
    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected DeliveryRecord() {
    }

    /** A new delivery starts {@link DeliveryStatus#IN_TRANSIT}: the carrier has the parcel. */
    public DeliveryRecord(Long orderId, Long customerId, String trackingNumber, UUID correlationId) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.trackingNumber = trackingNumber;
        this.correlationId = correlationId;
        this.status = DeliveryStatus.IN_TRANSIT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Applies one transition, or throws {@link InvalidDeliveryTransitionException} (→ 409). */
    public void advanceTo(DeliveryStatus next, Instant at) {
        if (!status.canMoveTo(next)) {
            throw new InvalidDeliveryTransitionException(orderId, status, next);
        }
        this.status = next;
        this.updatedAt = at;
        if (next == DeliveryStatus.DELIVERED) {
            this.deliveredAt = at;
        }
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

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
