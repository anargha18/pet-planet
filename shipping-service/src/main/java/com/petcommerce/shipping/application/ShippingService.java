package com.petcommerce.shipping.application;

import com.petcommerce.shipping.domain.model.Shipment;
import com.petcommerce.shipping.domain.model.ShipmentStatus;
import com.petcommerce.shipping.messaging.event.OrderConfirmedEvent;
import com.petcommerce.shipping.messaging.event.ShipmentCreatedEvent;
import com.petcommerce.shipping.messaging.publisher.ShipmentEventPublisher;
import com.petcommerce.shipping.persistence.entity.ProcessedEvent;
import com.petcommerce.shipping.persistence.repository.ProcessedEventRepository;
import com.petcommerce.shipping.persistence.repository.ShipmentRepository;
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

    public Optional<Shipment> getShipmentByOrderId(Long orderId) {
        return shipmentRepository.findByOrderId(orderId);
    }

    public List<Shipment> getShipmentsByCustomerId(Long customerId) {
        return shipmentRepository.findByCustomerId(customerId);
    }

    @Transactional
    public Shipment markDelivered(Long orderId) {
        Shipment shipment = shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("No shipment found for order " + orderId));
        shipment.setStatus(ShipmentStatus.DELIVERED);
        shipment.setDeliveredAt(Instant.now());
        return shipmentRepository.save(shipment);
    }

    @Transactional
    public Shipment handleOrderConfirmedEvent(OrderConfirmedEvent event) {
        if (event == null || event.orderId() == null) {
            return null;
        }

        if (processedEventRepository.existsById(event.eventId())) {
            log.info("Skipping already processed OrderConfirmedEvent {}", event.eventId());
            return shipmentRepository.findByOrderId(event.orderId()).orElse(null);
        }

        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate OrderConfirmedEvent {} detected and ignored", event.eventId());
            return shipmentRepository.findByOrderId(event.orderId()).orElse(null);
        }

        Shipment existing = shipmentRepository.findByOrderId(event.orderId()).orElse(null);
        if (existing != null) {
            log.info("Shipment already exists for order {} with tracking {}", event.orderId(), existing.getTrackingNumber());
            return existing;
        }

        Long customerId = event.customerId() != null ? event.customerId() : 101L;
        String trackingNumber = "PET-" + event.orderId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Shipment shipment = new Shipment(event.orderId(), customerId, ShipmentStatus.SHIPPED, trackingNumber);
        shipment.setShippedAt(Instant.now());
        shipmentRepository.save(shipment);

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

}
