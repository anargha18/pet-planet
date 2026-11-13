package com.petcommerce.order.messaging.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderItem;
import com.petcommerce.order.messaging.event.OrderCancelledEvent;
import com.petcommerce.order.messaging.event.OrderConfirmedEvent;
import com.petcommerce.order.messaging.event.OrderCreatedEvent;
import com.petcommerce.order.persistence.entity.OrderOutboxEvent;
import com.petcommerce.order.persistence.repository.OrderOutboxRepository;
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
 * Builds order events, stores them in the transactional outbox, and relays them to Kafka.
 * Must be called inside the business transaction so the outbox row commits with the order change.
 */
@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final OrderOutboxRepository orderOutboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(OrderOutboxRepository orderOutboxRepository,
                               KafkaTemplate<String, Object> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.orderOutboxRepository = orderOutboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.objectMapper.findAndRegisterModules();
    }

    public void publishOrderCreated(Order order) {
        if (order.getId() == null) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId,
                "OrderCreatedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                correlationId,
                order.getId(),
                order.getCustomerId(),
                petIds(order),
                order.getTotalAmount()
        );
        saveAndPublishOutbox(eventId, "order-created-events", "OrderCreatedEvent", order.getId(), correlationId, event);
    }

    public void publishOrderCancelled(Order order) {
        if (order.getId() == null) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        OrderCancelledEvent event = new OrderCancelledEvent(
                eventId,
                "OrderCancelledEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                correlationId,
                order.getId(),
                order.getCustomerId(),
                petIds(order)
        );
        saveAndPublishOutbox(eventId, "order-cancelled-events", "OrderCancelledEvent", order.getId(), correlationId, event);
    }

    public void publishOrderConfirmed(Order order, UUID correlationId) {
        UUID eventId = UUID.randomUUID();
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                eventId,
                "OrderConfirmedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                correlationId,
                order.getId(),
                order.getCustomerId(),
                petIds(order),
                order.getTotalAmount()
        );
        saveAndPublishOutbox(eventId, "order-confirmed-events", "OrderConfirmedEvent", order.getId(), correlationId, event);
    }

    @Scheduled(fixedDelayString = "${order.outbox-poll-delay-ms:5000}")
    @Transactional
    public void relayOutboxEvents() {
        List<OrderOutboxEvent> pending = orderOutboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OrderOutboxEvent outbox : pending) {
            try {
                Object payloadObj = deserializeOutboxPayload(outbox);
                String key = outbox.getOrderId() != null ? outbox.getOrderId().toString() : outbox.getEventId().toString();
                kafkaTemplate.send(outbox.getTopic(), key, payloadObj);
                outbox.setPublishedAt(Instant.now());
                orderOutboxRepository.save(outbox);
            } catch (Exception ex) {
                log.warn("Failed to relay order outbox event {}: {}", outbox.getEventId(), ex.getMessage());
            }
        }
    }

    private List<UUID> petIds(Order order) {
        return order.getItems().stream()
                .map(OrderItem::getPetId)
                .toList();
    }

    private void saveAndPublishOutbox(UUID eventId, String topic, String eventType, Long orderId, UUID correlationId, Object event) {
        OrderOutboxEvent outbox = new OrderOutboxEvent(
                eventId,
                topic,
                eventType,
                orderId,
                correlationId,
                serializePayload(event),
                Instant.now()
        );
        orderOutboxRepository.save(outbox);

        String key = orderId != null ? orderId.toString() : eventId.toString();
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

    private void sendAndMarkPublished(OrderOutboxEvent outbox, String key, Object event) {
        try {
            var future = kafkaTemplate.send(outbox.getTopic(), key, event);
            if (future != null) {
                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        outbox.setPublishedAt(Instant.now());
                        orderOutboxRepository.save(outbox);
                    }
                });
            }
        } catch (Exception ex) {
            log.warn("Deferred outbox event {} to scheduled poller: {}", outbox.getEventId(), ex.getMessage());
        }
    }

    private Object deserializeOutboxPayload(OrderOutboxEvent outbox) throws JsonProcessingException {
        return switch (outbox.getEventType()) {
            case "OrderCreatedEvent" -> objectMapper.readValue(outbox.getPayload(), OrderCreatedEvent.class);
            case "OrderCancelledEvent" -> objectMapper.readValue(outbox.getPayload(), OrderCancelledEvent.class);
            case "OrderConfirmedEvent" -> objectMapper.readValue(outbox.getPayload(), OrderConfirmedEvent.class);
            default -> objectMapper.readTree(outbox.getPayload());
        };
    }

    private String serializePayload(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize order event", ex);
        }
    }
}
