package com.petcommerce.delivery.application;

import com.petcommerce.delivery.domain.model.DeliveryRecord;
import com.petcommerce.delivery.domain.model.DeliveryStatus;
import com.petcommerce.delivery.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.delivery.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.delivery.messaging.publisher.DeliveryEventPublisher;
import com.petcommerce.delivery.persistence.entity.ProcessedEvent;
import com.petcommerce.delivery.persistence.repository.DeliveryRecordRepository;
import com.petcommerce.delivery.persistence.repository.ProcessedEventRepository;
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
public class DeliveryTrackingService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTrackingService.class);
    public static final String DELIVERY_STATUS_UPDATED_TOPIC = "delivery-status-updated-events";

    private final DeliveryRecordRepository deliveryRecordRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final DeliveryEventPublisher eventPublisher;

    public DeliveryTrackingService(
            DeliveryRecordRepository deliveryRecordRepository,
            ProcessedEventRepository processedEventRepository,
            DeliveryEventPublisher eventPublisher) {
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
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

        Long customerId = event.customerId() != null ? event.customerId() : 101L;
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
                customerId,
                delivery.getId(),
                delivery.getTrackingNumber(),
                delivery.getStatus().name()
        );

        eventPublisher.publish(DELIVERY_STATUS_UPDATED_TOPIC, event.orderId().toString(), statusEvent, delivery.getId(), event.orderId(), event.correlationId());

        log.info("Delivery tracking created for order {} (customer {}) with tracking {}", event.orderId(), customerId, delivery.getTrackingNumber());
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
                delivery.getCustomerId(),
                delivery.getId(),
                delivery.getTrackingNumber(),
                delivery.getStatus().name()
        );

        eventPublisher.publish(DELIVERY_STATUS_UPDATED_TOPIC, orderId.toString(), statusEvent, delivery.getId(), orderId, statusEvent.correlationId());
        return delivery;
    }

}
