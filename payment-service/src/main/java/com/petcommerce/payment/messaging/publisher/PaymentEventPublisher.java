package com.petcommerce.payment.messaging.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.payment.persistence.entity.PaymentOutboxEvent;
import com.petcommerce.payment.persistence.repository.PaymentOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes payment events to the transactional outbox and relays them to Kafka.
 * Call {@link #publish} inside the business transaction so the outbox row commits with the state change.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(PaymentOutboxRepository paymentOutboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.paymentOutboxRepository = paymentOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
    }

    public void publish(String topic, String key, Object event, UUID paymentId, Long orderId) {
        PaymentOutboxEvent outbox = new PaymentOutboxEvent(
                UUID.randomUUID(),
                topic,
                event.getClass().getSimpleName(),
                orderId,
                paymentId,
                serializePayload(event),
                Instant.now()
        );
        paymentOutboxRepository.save(outbox);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAndMarkPublished(outbox, key, event);
                }
            });
            return;
        }
        sendAndMarkPublished(outbox, key, event);
    }

    private void sendAndMarkPublished(PaymentOutboxEvent outbox, String key, Object event) {
        try {
            var future = kafkaTemplate.send(outbox.getTopic(), key, event);
            if (future != null) {
                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        outbox.setPublishedAt(Instant.now());
                        paymentOutboxRepository.save(outbox);
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("Deferred payment outbox event {} to scheduled poller: {}", outbox.getEventId(), ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${payment.outbox-poll-delay-ms:5000}")
    @Transactional
    public void relayOutboxEvents() {
        List<PaymentOutboxEvent> pending = paymentOutboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (PaymentOutboxEvent outbox : pending) {
            try {
                Object payloadObj = objectMapper.readTree(outbox.getPayload());
                String key = outbox.getOrderId() != null ? outbox.getOrderId().toString() : outbox.getEventId().toString();
                kafkaTemplate.send(outbox.getTopic(), key, payloadObj);
                outbox.setPublishedAt(Instant.now());
                paymentOutboxRepository.save(outbox);
            } catch (Exception ex) {
                log.warn("Failed to relay payment outbox event {}: {}", outbox.getEventId(), ex.getMessage());
            }
        }
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize payment event", ex);
        }
    }
}
