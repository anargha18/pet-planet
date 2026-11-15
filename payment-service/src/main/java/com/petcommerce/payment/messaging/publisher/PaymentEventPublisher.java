package com.petcommerce.payment.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.payment.persistence.entity.PaymentOutboxEvent;
import com.petcommerce.payment.persistence.repository.PaymentOutboxRepository;
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
public class PaymentEventPublisher {

    private final PaymentOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(PaymentOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, Object event, UUID paymentId, Long orderId) {
        String payload = OutboxPayloads.toJson(objectMapper, event);
        PaymentOutboxEvent outbox = new PaymentOutboxEvent(
                OutboxPayloads.eventIdOrRandom(objectMapper, payload),
                topic,
                event.getClass().getSimpleName(),
                orderId, paymentId,
                OutboxPayloads.uuidField(objectMapper, payload, "correlationId"),
                key,
                payload,
                Instant.now()
        );
        outboxRepository.save(outbox);
    }
}
