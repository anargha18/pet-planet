package com.petcommerce.delivery.domain.model;

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

/** One entry of a delivery's timeline (R-SVC-02): the status it reached, when, and an optional note. */
@Entity
@Table(name = "delivery_events")
public class DeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DeliveryStatus status;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected DeliveryEvent() {
    }

    public DeliveryEvent(DeliveryRecord delivery, String note, Instant occurredAt) {
        this.deliveryId = delivery.getId();
        this.orderId = delivery.getOrderId();
        this.status = delivery.getStatus();
        this.note = note;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeliveryId() {
        return deliveryId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
