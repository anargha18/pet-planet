package com.petcommerce.shipping.application;

import com.petcommerce.platform.security.AuthenticatedCaller;
import com.petcommerce.shipping.api.dto.ShipmentResponse;
import com.petcommerce.shipping.api.mapper.ShipmentMapper;
import com.petcommerce.shipping.domain.exception.ShipmentNotFoundException;
import com.petcommerce.shipping.domain.model.Shipment;
import com.petcommerce.shipping.domain.model.ShipmentStatus;
import com.petcommerce.shipping.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.shipping.messaging.event.OrderConfirmedEvent;
import com.petcommerce.shipping.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.shipping.messaging.publisher.ShipmentEventPublisher;
import com.petcommerce.shipping.persistence.repository.ProcessedEventRepository;
import com.petcommerce.shipping.persistence.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);
    public static final String SHIPMENT_CREATED_TOPIC = "shipment-created-events";

    private final ShipmentRepository shipmentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ShipmentEventPublisher eventPublisher;

    public ShippingService(
            ShipmentRepository shipmentRepository,
            ProcessedEventRepository processedEventRepository,
            ShipmentEventPublisher eventPublisher) {
        this.shipmentRepository = shipmentRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
    }


    /** The shipment of one order: 404 if none, 403 unless the caller owns the order or is an admin (R-SEC-04). */
    @Transactional(readOnly = true)
    public ShipmentResponse getForOrder(Long orderId, AuthenticatedCaller caller) {
        Shipment record = getShipmentByOrderId(orderId).orElseThrow(() -> new ShipmentNotFoundException(orderId));
        caller.requireAccessTo(record.getCustomerId());
        return ShipmentMapper.toResponse(record);
    }

    /** All shipments of a customer: users may only ask for their own, admins for anyone (R-SEC-04). */
    @Transactional(readOnly = true)
    public List<ShipmentResponse> getForCustomer(Long customerId, AuthenticatedCaller caller) {
        caller.requireAccessTo(customerId);
        return getShipmentsByCustomerId(customerId).stream().map(ShipmentMapper::toResponse).toList();
    }

    public Optional<Shipment> getShipmentByOrderId(Long orderId) {
        return shipmentRepository.findByOrderId(orderId);
    }

    public List<Shipment> getShipmentsByCustomerId(Long customerId) {
        return shipmentRepository.findByCustomerId(customerId);
    }

    @Transactional
    public Shipment handleOrderConfirmedEvent(OrderConfirmedEvent event) {
        if (event == null || event.orderId() == null) {
            return null;
        }

        if (processedEventRepository.markProcessed(event.eventId()) == 0) {
            log.info("Skipping already processed OrderConfirmedEvent {}", event.eventId());
            return shipmentRepository.findByOrderId(event.orderId()).orElse(null);
        }

        Shipment existing = shipmentRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Shipment already exists for order {} with tracking {}", event.orderId(), existing.getTrackingNumber());
            return existing;
        }

        if (event.customerId() == null) {
            // Required field: fail so the record is retried and then dead-lettered (R-EVT-07), never guessed.
            throw new IllegalArgumentException("Event " + event.eventId() + " for order " + event.orderId() + " has no customerId");
        }
        Long customerId = event.customerId();
        String trackingNumber = "PET-" + event.orderId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Shipment shipment = shipmentRepository.save(new Shipment(event.orderId(), customerId, trackingNumber));

        ShipmentCreatedEvent shipmentCreatedEvent = new ShipmentCreatedEvent(
                UUID.randomUUID(),
                "ShipmentCreatedEvent",
                1,
                event.aggregateId(),
                Instant.now(),
                event.correlationId(),
                event.orderId(),
                customerId,
                shipment.getId(),
                shipment.getTrackingNumber(),
                shipment.getStatus().name()
        );

        eventPublisher.publish(SHIPMENT_CREATED_TOPIC, event.orderId().toString(), shipmentCreatedEvent, shipment.getId(), event.orderId(), event.correlationId());

        log.info("Shipment created for order {} (customer {}) with tracking {}", event.orderId(), customerId, shipment.getTrackingNumber());
        return shipment;
    }

    /**
     * Mirrors delivery-tracking's status onto the shipment (R-SVC-03). Idempotent (processed_events) and monotonic:
     * a terminal shipment never changes. An event for an unknown order (e.g. history from before this consumer
     * existed) is logged and skipped rather than dead-lettered, because there is nothing to mirror it onto.
     */
    @Transactional
    public Shipment handleDeliveryStatusUpdatedEvent(DeliveryStatusUpdatedEvent event) {
        if (event == null || event.orderId() == null || event.eventId() == null) {
            return null;
        }
        if (processedEventRepository.markProcessed(event.eventId()) == 0) {
            log.info("Skipping already processed DeliveryStatusUpdatedEvent {}", event.eventId());
            return null;
        }
        Shipment shipment = shipmentRepository.findByOrderId(event.orderId()).orElse(null);
        if (shipment == null) {
            log.warn("No shipment for order {}; ignoring delivery status {}", event.orderId(), event.status());
            return null;
        }
        ShipmentStatus previous = shipment.getStatus();
        Instant at = event.timestamp() != null ? event.timestamp() : Instant.now();
        if (shipment.mirrorDelivery(ShipmentStatus.mirroring(event.status()), at)) {
            shipmentRepository.save(shipment);
            log.info("Shipment of order {} mirrored delivery status {}: {} -> {}",
                    event.orderId(), event.status(), previous, shipment.getStatus());
        }
        return shipment;
    }
}
