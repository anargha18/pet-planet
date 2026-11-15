package com.petcommerce.order.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderItem;
import com.petcommerce.order.messaging.event.OrderCancelledEvent;
import com.petcommerce.order.messaging.event.OrderConfirmedEvent;
import com.petcommerce.order.messaging.event.OrderCreatedEvent;
import com.petcommerce.order.messaging.event.OrderFailedEvent;
import com.petcommerce.order.persistence.entity.OrderOutboxEvent;
import com.petcommerce.order.persistence.repository.OrderOutboxRepository;
import com.petcommerce.platform.outbox.OutboxPayloads;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds order events and writes them to the transactional outbox. Must be called inside the business
 * transaction so the outbox row commits with the order change; the platform outbox relay publishes it (R-EVT-03).
 */
@Component
public class OrderEventPublisher {

    private final OrderOutboxRepository orderOutboxRepository;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(OrderOutboxRepository orderOutboxRepository, ObjectMapper objectMapper) {
        this.orderOutboxRepository = orderOutboxRepository;
        this.objectMapper = objectMapper;
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
        saveToOutbox(eventId, "order-created-events", "OrderCreatedEvent", order.getId(), correlationId, event);
    }

    /** reason: CUSTOMER_CANCELLED or RESERVATION_EXPIRED. Inventory releases pets; payment refunds or blocks charging. */
    public void publishOrderCancelled(Order order, String reason) {
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
                petIds(order),
                reason
        );
        saveToOutbox(eventId, "order-cancelled-events", "OrderCancelledEvent", order.getId(), correlationId, event);
    }

    /** Inventory releases the order's reservations; payment refunds a successful charge or blocks a later one. */
    public void publishOrderFailed(Order order, UUID correlationId, String reason) {
        UUID eventId = UUID.randomUUID();
        UUID effectiveCorrelationId = correlationId != null ? correlationId : UUID.randomUUID();
        OrderFailedEvent event = new OrderFailedEvent(
                eventId,
                "OrderFailedEvent",
                1,
                UUID.randomUUID(),
                Instant.now(),
                effectiveCorrelationId,
                order.getId(),
                order.getCustomerId(),
                petIds(order),
                reason
        );
        saveToOutbox(eventId, "order-failed-events", "OrderFailedEvent", order.getId(), effectiveCorrelationId, event);
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
        saveToOutbox(eventId, "order-confirmed-events", "OrderConfirmedEvent", order.getId(), correlationId, event);
    }

    private List<UUID> petIds(Order order) {
        return order.getItems().stream()
                .map(OrderItem::getPetId)
                .toList();
    }

    private void saveToOutbox(UUID eventId, String topic, String eventType, Long orderId, UUID correlationId, Object event) {
        String key = orderId != null ? orderId.toString() : eventId.toString();
        orderOutboxRepository.save(new OrderOutboxEvent(
                eventId,
                topic,
                eventType,
                orderId,
                correlationId,
                key,
                OutboxPayloads.toJson(objectMapper, event),
                Instant.now()
        ));
    }
}
