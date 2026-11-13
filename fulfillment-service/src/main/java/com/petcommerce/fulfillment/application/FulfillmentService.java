package com.petcommerce.fulfillment.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.fulfillment.persistence.entity.FulfillmentOutboxEvent;
import com.petcommerce.fulfillment.domain.model.FulfillmentRecord;
import com.petcommerce.fulfillment.domain.model.FulfillmentStatus;
import com.petcommerce.fulfillment.persistence.entity.ProcessedEvent;
import com.petcommerce.fulfillment.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.fulfillment.messaging.event.FulfillmentUpdatedEvent;
import com.petcommerce.fulfillment.persistence.repository.FulfillmentOutboxRepository;
import com.petcommerce.fulfillment.persistence.repository.FulfillmentRecordRepository;
import com.petcommerce.fulfillment.persistence.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);
    public static final String FULFILLMENT_UPDATED_TOPIC = "fulfillment-updated-events";

    private final FulfillmentRecordRepository fulfillmentRecordRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final FulfillmentOutboxRepository fulfillmentOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public FulfillmentService(
            FulfillmentRecordRepository fulfillmentRecordRepository,
            ProcessedEventRepository processedEventRepository,
            FulfillmentOutboxRepository fulfillmentOutboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.fulfillmentRecordRepository = fulfillmentRecordRepository;
        this.processedEventRepository = processedEventRepository;
        this.fulfillmentOutboxRepository = fulfillmentOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
    }

    public Optional<FulfillmentRecord> getFulfillmentByOrderId(Long orderId) {
        return fulfillmentRecordRepository.findByOrderId(orderId);
    }

    public List<FulfillmentRecord> getFulfillmentsByCustomerId(Long customerId) {
        return fulfillmentRecordRepository.findByCustomerId(customerId);
    }

    @Transactional
    public FulfillmentRecord handleDeliveryStatusUpdatedEvent(DeliveryStatusUpdatedEvent event) {
        if (event == null || event.orderId() == null) {
            return null;
        }

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Skipping already processed DeliveryStatusUpdatedEvent {}", event.eventId());
            return fulfillmentRecordRepository.findByOrderId(event.orderId()).orElse(null);
        }

        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate DeliveryStatusUpdatedEvent {} detected and ignored", event.eventId());
            return fulfillmentRecordRepository.findByOrderId(event.orderId()).orElse(null);
        }

        FulfillmentStatus status = switch (event.status()) {
            case "IN_TRANSIT" -> FulfillmentStatus.SHIPPED;
            case "DELIVERED" -> FulfillmentStatus.DELIVERED;
            default -> FulfillmentStatus.ESCALATED;
        };

        FulfillmentRecord existing = fulfillmentRecordRepository.findByOrderId(event.orderId()).orElse(null);
        FulfillmentRecord record;
        if (existing != null) {
            existing.setStatus(status);
            existing.setNotes("Delivery status updated to " + event.status());
            record = fulfillmentRecordRepository.save(existing);
            log.info("Updated existing fulfillment record for order {} to status {}", event.orderId(), record.getStatus());
        } else {
            Long customerId = event.customerId() != null ? event.customerId() : 101L;
            record = new FulfillmentRecord(
                    event.orderId(),
                    customerId,
                    event.trackingNumber(),
                    status,
                    "Delivery status observed as " + event.status()
            );
            fulfillmentRecordRepository.save(record);
            log.info("Fulfillment record created for order {} with status {}", event.orderId(), record.getStatus());
        }

        FulfillmentUpdatedEvent updatedEvent = new FulfillmentUpdatedEvent(
                UUID.randomUUID(),
                "FulfillmentUpdatedEvent",
                1,
                event.aggregateId(),
                Instant.now(),
                event.correlationId(),
                event.orderId(),
                record.getId(),
                record.getTrackingNumber(),
                record.getStatus().name(),
                record.getNotes()
        );

        publishAfterCommit(FULFILLMENT_UPDATED_TOPIC, event.orderId().toString(), updatedEvent, record.getId(), event.orderId(), event.correlationId());
        return record;
    }

    private void publishAfterCommit(String topic, String key, Object event, UUID fulfillmentId, Long orderId, UUID correlationId) {
        FulfillmentOutboxEvent outbox = new FulfillmentOutboxEvent(
                UUID.randomUUID(),
                topic,
                event.getClass().getSimpleName(),
                orderId,
                correlationId,
                serializePayload(event),
                Instant.now()
        );
        fulfillmentOutboxRepository.save(outbox);

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

    private void sendAndMarkPublished(FulfillmentOutboxEvent outbox, String key, Object event) {
        try {
            var future = kafkaTemplate.send(outbox.getTopic(), key, event);
            if (future != null) {
                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        outbox.setPublishedAt(Instant.now());
                        fulfillmentOutboxRepository.save(outbox);
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("Deferred fulfillment outbox event {} to scheduled poller: {}", outbox.getEventId(), ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${fulfillment.outbox-poll-delay-ms:5000}")
    @Transactional
    public void relayOutboxEvents() {
        List<FulfillmentOutboxEvent> pending = fulfillmentOutboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (FulfillmentOutboxEvent outbox : pending) {
            try {
                Object payloadObj = objectMapper.readTree(outbox.getPayload());
                String key = outbox.getOrderId() != null ? outbox.getOrderId().toString() : outbox.getEventId().toString();
                kafkaTemplate.send(outbox.getTopic(), key, payloadObj);
                outbox.setPublishedAt(Instant.now());
                fulfillmentOutboxRepository.save(outbox);
            } catch (Exception ex) {
                log.warn("Failed to relay fulfillment outbox event {}: {}", outbox.getEventId(), ex.getMessage());
            }
        }
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize fulfillment event", ex);
        }
    }
}
