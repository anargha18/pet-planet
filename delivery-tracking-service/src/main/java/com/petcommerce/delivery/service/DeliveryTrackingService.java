package com.petcommerce.delivery.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.delivery.entity.DeliveryOutboxEvent;
import com.petcommerce.delivery.entity.DeliveryRecord;
import com.petcommerce.delivery.entity.DeliveryStatus;
import com.petcommerce.delivery.entity.ProcessedEvent;
import com.petcommerce.delivery.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.delivery.event.ShipmentCreatedEvent;
import com.petcommerce.delivery.repository.DeliveryOutboxRepository;
import com.petcommerce.delivery.repository.DeliveryRecordRepository;
import com.petcommerce.delivery.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeliveryTrackingService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTrackingService.class);
    public static final String DELIVERY_STATUS_UPDATED_TOPIC = "delivery-status-updated-events";

    private final DeliveryRecordRepository deliveryRecordRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final DeliveryOutboxRepository deliveryOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DeliveryTrackingService(
            DeliveryRecordRepository deliveryRecordRepository,
            ProcessedEventRepository processedEventRepository,
            DeliveryOutboxRepository deliveryOutboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.processedEventRepository = processedEventRepository;
        this.deliveryOutboxRepository = deliveryOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
    }

    public Optional<DeliveryRecord> getDeliveryByOrderId(Long orderId) {
        return deliveryRecordRepository.findByOrderId(orderId);
    }

    public List<DeliveryRecord> getDeliveriesByCustomerId(Long customerId) {
        return deliveryRecordRepository.findByCustomerId(customerId);
    }

    @Transactional
    public DeliveryRecord handleShipmentCreatedEvent(ShipmentCreatedEvent event) {
        if (event == null || event.orderId() == null) {
            return null;
        }

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Skipping already processed ShipmentCreatedEvent {}", event.eventId());
            return deliveryRecordRepository.findByOrderId(event.orderId()).orElse(null);
        }

        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate ShipmentCreatedEvent {} detected and ignored", event.eventId());
            return deliveryRecordRepository.findByOrderId(event.orderId()).orElse(null);
        }

        DeliveryRecord existing = deliveryRecordRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Delivery already exists for order {} with tracking {}", event.orderId(), existing.getTrackingNumber());
            return existing;
        }

        Long customerId = 101L;
        DeliveryRecord delivery = new DeliveryRecord(event.orderId(), customerId, event.trackingNumber(), DeliveryStatus.IN_TRANSIT);
        deliveryRecordRepository.save(delivery);

        DeliveryStatusUpdatedEvent statusEvent = new DeliveryStatusUpdatedEvent(
                UUID.randomUUID(),
                "DeliveryStatusUpdatedEvent",
                1,
                event.aggregateId(),
                Instant.now(),
                event.correlationId(),
                event.orderId(),
                delivery.getId(),
                delivery.getTrackingNumber(),
                delivery.getStatus().name()
        );

        publishAfterCommit(DELIVERY_STATUS_UPDATED_TOPIC, event.orderId().toString(), statusEvent, delivery.getId(), event.orderId(), event.correlationId());

        log.info("Delivery tracking created for order {} with tracking {}", event.orderId(), delivery.getTrackingNumber());
        return delivery;
    }

    @Transactional
    public DeliveryRecord markDelivered(Long orderId) {
        DeliveryRecord delivery = deliveryRecordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No delivery found for order " + orderId));

        delivery.setStatus(DeliveryStatus.DELIVERED);
        deliveryRecordRepository.save(delivery);

        DeliveryStatusUpdatedEvent statusEvent = new DeliveryStatusUpdatedEvent(
                UUID.randomUUID(),
                "DeliveryStatusUpdatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                orderId,
                delivery.getId(),
                delivery.getTrackingNumber(),
                delivery.getStatus().name()
        );

        publishAfterCommit(DELIVERY_STATUS_UPDATED_TOPIC, orderId.toString(), statusEvent, delivery.getId(), orderId, statusEvent.correlationId());
        return delivery;
    }

    private void publishAfterCommit(String topic, String key, Object event, UUID deliveryId, Long orderId, UUID correlationId) {
        deliveryOutboxRepository.save(new DeliveryOutboxEvent(
                UUID.randomUUID(),
                topic,
                event.getClass().getSimpleName(),
                orderId,
                correlationId,
                serializePayload(event),
                Instant.now()
        ));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaTemplate.send(topic, key, event);
                }
            });
            return;
        }

        kafkaTemplate.send(topic, key, event);
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize delivery event", ex);
        }
    }
}
