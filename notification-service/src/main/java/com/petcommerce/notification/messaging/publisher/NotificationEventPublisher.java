package com.petcommerce.notification.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.notification.persistence.entity.NotificationOutboxEvent;
import com.petcommerce.notification.persistence.repository.NotificationOutboxRepository;
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
public class NotificationEventPublisher {

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public NotificationEventPublisher(NotificationOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, Object event, UUID notificationId, Long orderId, UUID correlationId) {
        String payload = OutboxPayloads.toJson(objectMapper, event);
        NotificationOutboxEvent outbox = new NotificationOutboxEvent(
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
