package com.petcommerce.delivery.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.delivery.persistence.entity.DeliveryOutboxEvent;
import com.petcommerce.delivery.persistence.repository.DeliveryOutboxRepository;
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
public class DeliveryEventPublisher {

    private final DeliveryOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DeliveryEventPublisher(DeliveryOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, Object event, UUID deliveryId, Long orderId, UUID correlationId) {
        String payload = OutboxPayloads.toJson(objectMapper, event);
        DeliveryOutboxEvent outbox = new DeliveryOutboxEvent(
                OutboxPayloads.eventIdOrRandom(objectMapper, payload),
                topic,
                event.getClass().getSimpleName(),
                orderId,
                correlationId,
                key,
                payload,
                Instant.now()
        );
        outboxRepository.save(outbox);
    }
}
