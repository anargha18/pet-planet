package com.petcommerce.fulfillment.application;

import com.petcommerce.fulfillment.domain.model.FulfillmentRecord;
import com.petcommerce.fulfillment.domain.model.FulfillmentStatus;
import com.petcommerce.fulfillment.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.fulfillment.messaging.event.FulfillmentUpdatedEvent;
import com.petcommerce.fulfillment.messaging.publisher.FulfillmentEventPublisher;
import com.petcommerce.fulfillment.persistence.entity.ProcessedEvent;
import com.petcommerce.fulfillment.persistence.repository.FulfillmentRecordRepository;
import com.petcommerce.fulfillment.persistence.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final FulfillmentEventPublisher eventPublisher;

    public FulfillmentService(
            FulfillmentRecordRepository fulfillmentRecordRepository,
            ProcessedEventRepository processedEventRepository,
            FulfillmentEventPublisher eventPublisher) {
        this.fulfillmentRecordRepository = fulfillmentRecordRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
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

        eventPublisher.publish(FULFILLMENT_UPDATED_TOPIC, event.orderId().toString(), updatedEvent, record.getId(), event.orderId(), event.correlationId());
        return record;
    }

}
