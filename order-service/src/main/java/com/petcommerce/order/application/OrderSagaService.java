package com.petcommerce.order.application;

import com.petcommerce.order.domain.exception.OrderNotFoundException;
import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderStatus;
import com.petcommerce.order.domain.policy.OrderStateMachine;
import com.petcommerce.order.messaging.event.InventorySaleConflictEvent;
import com.petcommerce.order.messaging.event.PaymentFailedEvent;
import com.petcommerce.order.messaging.event.PetReservationExpiredEvent;
import com.petcommerce.order.messaging.event.PetReservationFailedEvent;
import com.petcommerce.order.messaging.event.PetReservedEvent;
import com.petcommerce.order.messaging.event.PetsSoldEvent;
import com.petcommerce.order.messaging.publisher.OrderEventPublisher;
import com.petcommerce.order.persistence.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Reacts to checkout-saga events and moves the order through {@link OrderStateMachine} (R-SAGA-01/02).
 * Every terminal failure publishes an event (OrderCancelled / OrderFailed) so inventory releases the pets and
 * payment refunds or blocks charging. Illegal or repeated transitions are ignored, which makes every handler
 * idempotent.
 */
@Service
public class OrderSagaService {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaService.class);

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    public OrderSagaService(OrderRepository orderRepository, OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
    }

    @Transactional
    public void handlePetReserved(PetReservedEvent event) {
        Order order = findOrder(event.orderId());
        if (transition(order, OrderStatus.PAYMENT_PENDING, "pets reserved (reservation " + event.reservationId() + ")")) {
            orderRepository.save(order);
        }
    }

    /** The sale is complete only when inventory marked every pet SOLD: this is the point of confirmation. */
    @Transactional
    public void handlePetsSold(PetsSoldEvent event) {
        Order order = findOrder(event.orderId());
        if (transition(order, OrderStatus.CONFIRMED, "payment succeeded and pets sold")) {
            orderRepository.save(order);
            orderEventPublisher.publishOrderConfirmed(order, event.correlationId());
        }
    }

    @Transactional
    public void handlePetReservationFailed(PetReservationFailedEvent event) {
        fail(findOrder(event.orderId()), event.correlationId(), "RESERVATION_FAILED:" + event.reason());
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        fail(findOrder(event.orderId()), event.correlationId(), "PAYMENT_FAILED:" + event.reason());
    }

    @Transactional
    public void handleSaleConflict(InventorySaleConflictEvent event) {
        fail(findOrder(event.orderId()), event.correlationId(), "SALE_CONFLICT:" + event.reason());
    }

    @Transactional
    public void handlePetReservationExpired(PetReservationExpiredEvent event) {
        Order order = findOrder(event.orderId());
        if (transition(order, OrderStatus.CANCELLED, "reservation expired")) {
            orderRepository.save(order);
            orderEventPublisher.publishOrderCancelled(order, "RESERVATION_EXPIRED");
        }
    }

    private void fail(Order order, UUID correlationId, String reason) {
        if (transition(order, OrderStatus.FAILED, reason)) {
            orderRepository.save(order);
            orderEventPublisher.publishOrderFailed(order, correlationId, reason);
        }
    }

    /** Applies the transition if the state machine allows it; otherwise logs and returns false (no change). */
    private boolean transition(Order order, OrderStatus next, String cause) {
        OrderStatus current = order.getStatus();
        if (!OrderStateMachine.canTransition(current, next)) {
            log.info("Order {} stays {}: ignoring transition to {} ({})", order.getId(), current, next, cause);
            return false;
        }
        order.setStatus(next);
        log.info("Order {} {} -> {} ({})", order.getId(), current, next, cause);
        return true;
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
