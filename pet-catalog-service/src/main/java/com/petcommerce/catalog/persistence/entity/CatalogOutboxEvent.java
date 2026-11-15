package com.petcommerce.catalog.persistence.entity;

import com.petcommerce.platform.outbox.OutboxMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row: written in the same database transaction as the business change and
 * published to Kafka afterwards by the platform outbox relay (platform-commons).
 */
@Entity
@Table(name = "catalog_outbox")
public class CatalogOutboxEvent implements OutboxMessage {

    private static final int MAX_ERROR_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "pet_id")
    private UUID petId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Column(name = "message_key")
    private String messageKey;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    protected CatalogOutboxEvent() {
    }

    public CatalogOutboxEvent(UUID eventId, String topic, String eventType, UUID petId, UUID correlationId, String messageKey, String payload, Instant createdAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.eventType = eventType;
        this.petId = petId;
        this.correlationId = correlationId;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public String getEventType() {
        return eventType;
    }

    public UUID getPetId() {
        return petId;
    }

    @Override
    public UUID getCorrelationId() {
        return correlationId;
    }

    /** The key chosen by the publisher; rows written before message_key existed fall back to petId/eventId. */
    @Override
    public String getMessageKey() {
        if (messageKey != null) {
            return messageKey;
        }
        return petId != null ? petId.toString() : eventId.toString();
    }

    @Override
    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    @Override
    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    @Override
    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
