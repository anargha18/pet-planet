package com.petcommerce.shipping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.shipping.entity.ProcessedEvent;
import com.petcommerce.shipping.entity.Shipment;
import com.petcommerce.shipping.entity.ShipmentOutboxEvent;
import com.petcommerce.shipping.entity.ShipmentStatus;
import com.petcommerce.shipping.event.OrderConfirmedEvent;
import com.petcommerce.shipping.event.ShipmentCreatedEvent;
import com.petcommerce.shipping.repository.ProcessedEventRepository;
import com.petcommerce.shipping.repository.ShipmentOutboxRepository;
import com.petcommerce.shipping.repository.ShipmentRepository;
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
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);
    public static final String SHIPMENT_CREATED_TOPIC = "shipment-created-events";

    private final ShipmentRepository shipmentRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ShipmentOutboxRepository shipmentOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ShippingService(
            ShipmentRepository shipmentRepository,
            ProcessedEventRepository processedEventRepository,
            ShipmentOutboxRepository shipmentOutboxRepository,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.shipmentRepository = shipmentRepository;
        this.processedEventRepository = processedEventRepository;
        this.shipmentOutboxRepository = shipmentOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
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

        publishAfterCommit(SHIPMENT_CREATED_TOPIC, event.orderId().toString(), shipmentCreatedEvent, shipment.getId(), event.orderId(), event.correlationId());

        log.info("Shipment created for order {} (customer {}) with tracking {}", event.orderId(), customerId, shipment.getTrackingNumber());
        return shipment;
    }

    private void publishAfterCommit(String topic, String key, Object event, UUID shipmentId, Long orderId, UUID correlationId) {
        ShipmentOutboxEvent outbox = new ShipmentOutboxEvent(
                UUID.randomUUID(),
                topic,
                event.getClass().getSimpleName(),
                orderId,
                correlationId,
                serializePayload(event),
                Instant.now()
        );
        shipmentOutboxRepository.save(outbox);

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

    private void sendAndMarkPublished(ShipmentOutboxEvent outbox, String key, Object event) {
        try {
            var future = kafkaTemplate.send(outbox.getTopic(), key, event);
            if (future != null) {
                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        outbox.setPublishedAt(Instant.now());
                        shipmentOutboxRepository.save(outbox);
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("Deferred shipment outbox event {} to scheduled poller: {}", outbox.getEventId(), ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${shipping.outbox-poll-delay-ms:5000}")
    @Transactional
    public void relayOutboxEvents() {
        List<ShipmentOutboxEvent> pending = shipmentOutboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (ShipmentOutboxEvent outbox : pending) {
            try {
                Object payloadObj = objectMapper.readTree(outbox.getPayload());
                String key = outbox.getOrderId() != null ? outbox.getOrderId().toString() : outbox.getEventId().toString();
                kafkaTemplate.send(outbox.getTopic(), key, payloadObj);
                outbox.setPublishedAt(Instant.now());
                shipmentOutboxRepository.save(outbox);
            } catch (Exception ex) {
                log.warn("Failed to relay shipping outbox event {}: {}", outbox.getEventId(), ex.getMessage());
            }
        }
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize shipping event", ex);
        }
    }
}
