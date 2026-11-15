package com.petcommerce.delivery.application;

import com.petcommerce.delivery.api.dto.DeliveryResponse;
import com.petcommerce.delivery.api.mapper.DeliveryRecordMapper;
import com.petcommerce.delivery.domain.exception.DeliveryNotFoundException;
import com.petcommerce.delivery.domain.model.DeliveryEvent;
import com.petcommerce.delivery.domain.model.DeliveryRecord;
import com.petcommerce.delivery.domain.model.DeliveryStatus;
import com.petcommerce.delivery.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.delivery.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.delivery.messaging.publisher.DeliveryEventPublisher;
import com.petcommerce.delivery.persistence.repository.DeliveryEventRepository;
import com.petcommerce.delivery.persistence.repository.DeliveryRecordRepository;
import com.petcommerce.delivery.persistence.repository.ProcessedEventRepository;
import com.petcommerce.platform.security.AuthenticatedCaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns carrier progress (R-SAGA-06, R-SVC-02): starts a delivery when a shipment is created, lets an admin advance
 * it, records every status in the timeline and publishes {@code DeliveryStatusUpdatedEvent} through the outbox.
 */
@Service
public class DeliveryTrackingService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTrackingService.class);
    public static final String DELIVERY_STATUS_UPDATED_TOPIC = "delivery-status-updated-events";

    private final DeliveryRecordRepository deliveryRecordRepository;
    private final DeliveryEventRepository deliveryEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final DeliveryEventPublisher eventPublisher;

    public DeliveryTrackingService(
            DeliveryRecordRepository deliveryRecordRepository,
            DeliveryEventRepository deliveryEventRepository,
            ProcessedEventRepository processedEventRepository,
            DeliveryEventPublisher eventPublisher) {
        this.deliveryRecordRepository = deliveryRecordRepository;
        this.deliveryEventRepository = deliveryEventRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
    }

    /** The delivery of one order: 404 if none, 403 unless the caller owns the order or is an admin (R-SEC-04). */
    @Transactional(readOnly = true)
    public DeliveryResponse getForOrder(Long orderId, AuthenticatedCaller caller) {
        DeliveryRecord record = deliveryRecordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException(orderId));
        caller.requireAccessTo(record.getCustomerId());
        return DeliveryRecordMapper.toResponse(record, deliveryEventRepository.findByOrderIdOrderByOccurredAtAsc(orderId));
    }

    /** All deliveries of a customer: users may only ask for their own, admins for anyone (R-SEC-04). */
    @Transactional(readOnly = true)
    public List<DeliveryResponse> getForCustomer(Long customerId, AuthenticatedCaller caller) {
        caller.requireAccessTo(customerId);
        List<DeliveryRecord> records = deliveryRecordRepository.findByCustomerId(customerId);
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DeliveryEvent>> timelines = deliveryEventRepository
                .findByOrderIdInOrderByOccurredAtAsc(records.stream().map(DeliveryRecord::getOrderId).toList())
                .stream()
                .collect(Collectors.groupingBy(DeliveryEvent::getOrderId));
        return records.stream()
                .map(r -> DeliveryRecordMapper.toResponse(r, timelines.getOrDefault(r.getOrderId(), List.of())))
                .toList();
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
        DeliveryRecord delivery = deliveryRecordRepository.save(
                new DeliveryRecord(event.orderId(), event.customerId(), event.trackingNumber(), event.correlationId()));
        recordAndPublish(delivery, null, "Handed to the carrier");

        log.info("Delivery tracking created for order {} (customer {}) with tracking {}",
                event.orderId(), event.customerId(), delivery.getTrackingNumber());
        return delivery;
    }

    /**
     * Admin action (R-SAGA-06): advance the delivery of an order. Illegal moves (e.g. back to IN_TRANSIT, or anything
     * after DELIVERED) throw {@code InvalidDeliveryTransitionException} → 409.
     */
    @Transactional
    public DeliveryResponse advance(Long orderId, DeliveryStatus next, String note) {
        DeliveryRecord delivery = deliveryRecordRepository.findByOrderId(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException(orderId));
        DeliveryStatus previous = delivery.getStatus();
        delivery.advanceTo(next, Instant.now());
        deliveryRecordRepository.save(delivery);
        recordAndPublish(delivery, previous, note);
        log.info("Delivery of order {} moved {} -> {}", orderId, previous, next);
        return DeliveryRecordMapper.toResponse(delivery, deliveryEventRepository.findByOrderIdOrderByOccurredAtAsc(orderId));
    }

    /** Timeline row + outbox event for the delivery's current status, in the caller's transaction. */
    private void recordAndPublish(DeliveryRecord delivery, DeliveryStatus previous, String note) {
        deliveryEventRepository.save(new DeliveryEvent(delivery, note, delivery.getUpdatedAt()));

        UUID correlationId = delivery.getCorrelationId() != null ? delivery.getCorrelationId() : UUID.randomUUID();
        DeliveryStatusUpdatedEvent statusEvent = new DeliveryStatusUpdatedEvent(
                UUID.randomUUID(),
                "DeliveryStatusUpdatedEvent",
                1,
                delivery.getId(),
                Instant.now(),
                correlationId,
                delivery.getOrderId(),
                delivery.getCustomerId(),
                delivery.getId(),
                delivery.getTrackingNumber(),
                delivery.getStatus().name(),
                previous == null ? null : previous.name(),
                note
        );
        eventPublisher.publish(DELIVERY_STATUS_UPDATED_TOPIC, delivery.getOrderId().toString(), statusEvent,
                delivery.getId(), delivery.getOrderId(), correlationId);
    }
}
