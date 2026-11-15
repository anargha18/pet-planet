package com.petcommerce.inventory.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.inventory.persistence.entity.InventoryOutboxEvent;
import com.petcommerce.inventory.persistence.repository.InventoryOutboxRepository;
import com.petcommerce.platform.outbox.OutboxPayloads;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes this service's events to its transactional outbox. Call {@link #publish} inside the business
 * transaction: the row commits (or rolls back) with the state change, and the platform outbox relay
 * publishes it to Kafka afterwards (R-EVT-03).
 */
@Component
public class InventoryEventPublisher {

    private final InventoryOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public InventoryEventPublisher(InventoryOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, Object event, String eventType, UUID petId, Long orderId, UUID correlationId) {
        String payload = OutboxPayloads.toJson(objectMapper, event);
        InventoryOutboxEvent outbox = new InventoryOutboxEvent(
                OutboxPayloads.eventIdOrRandom(objectMapper, payload),
                topic,
                eventType,
                petId, orderId,
                correlationId,
                key,
                payload,
                Instant.now()
        );
        outboxRepository.save(outbox);
    }
}
