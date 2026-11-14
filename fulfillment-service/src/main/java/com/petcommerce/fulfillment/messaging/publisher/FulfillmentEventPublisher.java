package com.petcommerce.fulfillment.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.fulfillment.persistence.entity.FulfillmentOutboxEvent;
import com.petcommerce.fulfillment.persistence.repository.FulfillmentOutboxRepository;
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
public class FulfillmentEventPublisher {

    private final FulfillmentOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FulfillmentEventPublisher(FulfillmentOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, Object event, UUID fulfillmentId, Long orderId, UUID correlationId) {
        String payload = OutboxPayloads.toJson(objectMapper, event);
        FulfillmentOutboxEvent outbox = new FulfillmentOutboxEvent(
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
