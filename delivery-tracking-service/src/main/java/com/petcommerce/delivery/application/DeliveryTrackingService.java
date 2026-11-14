package com.petcommerce.delivery.application;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.delivery.api.dto.DeliveryResponse;
import com.petcommerce.delivery.api.mapper.DeliveryRecordMapper;
import com.petcommerce.delivery.domain.exception.DeliveryNotFoundException;
import com.petcommerce.delivery.domain.model.DeliveryRecord;
import com.petcommerce.delivery.domain.model.DeliveryStatus;
import com.petcommerce.delivery.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.delivery.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.delivery.messaging.publisher.DeliveryEventPublisher;
import com.petcommerce.delivery.persistence.repository.DeliveryRecordRepository;
import com.petcommerce.delivery.persistence.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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


    /** The delivery of one order: 404 if none, 403 unless the caller owns the order or is an admin (R-SEC-04). */
    @Transactional(readOnly = true)
    public DeliveryResponse getForOrder(Long orderId, AuthenticatedCaller caller) {
        DeliveryRecord record = getDeliveryByOrderId(orderId).orElseThrow(() -> new DeliveryNotFoundException(orderId));
        caller.requireAccessTo(record.getCustomerId());
        return DeliveryRecordMapper.toResponse(record);
    }

    /** All deliverys of a customer: users may only ask for their own, admins for anyone (R-SEC-04). */
    @Transactional(readOnly = true)
    public List<DeliveryResponse> getForCustomer(Long customerId, AuthenticatedCaller caller) {
        caller.requireAccessTo(customerId);
        return getDeliveriesByCustomerId(customerId).stream().map(DeliveryRecordMapper::toResponse).toList();
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

        if (processedEventRepository.markProcessed(event.eventId()) == 0) {
            log.info("Skipping already processed ShipmentCreatedEvent {}", event.eventId());
            return deliveryRecordRepository.findByOrderId(event.orderId()).orElse(null);
        }

        DeliveryRecord existing = deliveryRecordRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Delivery already exists for order {} with tracking {}", event.orderId(), existing.getTrackingNumber());
            return existing;
        }

        if (event.customerId() == null) {
            // Required field: fail so the record is retried and then dead-lettered (R-EVT-07), never guessed.
            throw new IllegalArgumentException("Event " + event.eventId() + " for order " + event.orderId() + " has no customerId");
        }
        Long customerId = event.customerId();
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
                .orElseThrow(() -> new DeliveryNotFoundException(orderId));

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
