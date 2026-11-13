package com.petcommerce.inventory.messaging.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.inventory.persistence.entity.InventoryOutboxEvent;
import com.petcommerce.inventory.persistence.repository.InventoryOutboxRepository;
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
 * Writes inventory events to the transactional outbox and relays them to Kafka.
 * Call {@link #publish} inside the business transaction so the outbox row commits with the state change.
 */
@Component
public class InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);

    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InventoryEventPublisher(InventoryOutboxRepository inventoryOutboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.inventoryOutboxRepository = inventoryOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
    }

    public void publish(String topic, String key, Object event, String eventType, UUID petId, Long orderId, UUID correlationId) {
        InventoryOutboxEvent outbox = new InventoryOutboxEvent(
                UUID.randomUUID(),
                topic,
                eventType,
                petId,
                orderId,
                correlationId,
                serializePayload(event),
                Instant.now()
        );
        inventoryOutboxRepository.save(outbox);

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

    private void sendAndMarkPublished(InventoryOutboxEvent outbox, String key, Object event) {
        try {
            var future = kafkaTemplate.send(outbox.getTopic(), key, event);
            if (future != null) {
                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        outbox.setPublishedAt(Instant.now());
                        inventoryOutboxRepository.save(outbox);
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("Deferred inventory outbox event {} to scheduled poller: {}", outbox.getEventId(), ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${inventory.outbox-poll-delay-ms:5000}")
    @Transactional
    public void relayOutboxEvents() {
        List<InventoryOutboxEvent> pending = inventoryOutboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (InventoryOutboxEvent outbox : pending) {
            try {
                Object payloadObj = objectMapper.readTree(outbox.getPayload());
                String key = outbox.getPetId() != null ? outbox.getPetId().toString() : outbox.getEventId().toString();
                kafkaTemplate.send(outbox.getTopic(), key, payloadObj);
                outbox.setPublishedAt(Instant.now());
                inventoryOutboxRepository.save(outbox);
            } catch (Exception ex) {
                log.warn("Failed to relay inventory outbox event {}: {}", outbox.getEventId(), ex.getMessage());
            }
        }
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize inventory event", e);
        }
    }
}
