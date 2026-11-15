package com.petcommerce.fulfillment.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One timeline entry of an order (R-SVC-01). Keyed by the source event's {@code eventId}, so inserting it also makes
 * the projection idempotent: a replayed event finds its row and changes nothing.
 */
@Entity
@Table(name = "order_tracking_events")
public class OrderTrackingEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected OrderTrackingEvent() {
    }

    public OrderTrackingEvent(UUID eventId, Long orderId, String eventType, String source, String description, Instant occurredAt) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.source = source;
        this.description = description;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSource() {
        return source;
    }

    public String getDescription() {
        return description;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
