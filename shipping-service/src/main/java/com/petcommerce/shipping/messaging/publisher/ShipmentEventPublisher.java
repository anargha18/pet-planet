package com.petcommerce.shipping.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.platform.outbox.OutboxPayloads;
import com.petcommerce.shipping.persistence.entity.ShipmentOutboxEvent;
import com.petcommerce.shipping.persistence.repository.ShipmentOutboxRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes this service's events to its transactional outbox. Call {@link #publish} inside the business
 * transaction: the row commits (or rolls back) with the state change, and the platform outbox relay
 * publishes it to Kafka afterwards (R-EVT-03).
 */
@Component
public class ShipmentEventPublisher {

    private final ShipmentOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ShipmentEventPublisher(ShipmentOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, Object event, UUID shipmentId, Long orderId, UUID correlationId) {
        String payload = OutboxPayloads.toJson(objectMapper, event);
        ShipmentOutboxEvent outbox = new ShipmentOutboxEvent(
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
