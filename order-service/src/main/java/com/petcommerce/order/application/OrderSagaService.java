package com.petcommerce.order.application;

import com.petcommerce.order.domain.exception.OrderNotFoundException;
import com.petcommerce.order.domain.model.Order;
import com.petcommerce.order.domain.model.OrderStatus;
import com.petcommerce.order.messaging.event.PaymentFailedEvent;
import com.petcommerce.order.messaging.event.PaymentSucceededEvent;
import com.petcommerce.order.messaging.event.PetReservationExpiredEvent;
import com.petcommerce.order.messaging.event.PetReservationFailedEvent;
import com.petcommerce.order.messaging.event.PetReservedEvent;
import com.petcommerce.order.messaging.publisher.OrderEventPublisher;
import com.petcommerce.order.persistence.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reacts to checkout-saga events from inventory and payment and moves the order through its states. */
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
        if (event == null || event.orderId() == null) {
            return;
        }
        Order order = findOrder(event.orderId());
        if (order.getStatus() == OrderStatus.CREATED) {
            order.setStatus(OrderStatus.PAYMENT_PENDING);
            orderRepository.save(order);
            log.info("Order {} transitioned to PAYMENT_PENDING after pet reservation {}", order.getId(), event.reservationId());
        }
    }

    @Transactional
    public void handlePetReservationFailed(PetReservationFailedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        Order order = findOrder(event.orderId());
        if (order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            log.warn("Order {} marked as FAILED due to inventory reservation failure: {}", order.getId(), event.reason());
        }
    }

    @Transactional
    public void handlePetReservationExpired(PetReservationExpiredEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        Order order = findOrder(event.orderId());
        if (order.getStatus() == OrderStatus.CREATED || order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            log.warn("Order {} marked as CANCELLED after inventory reservation expired", order.getId());
        }
    }

    @Transactional
    public void handlePaymentSucceeded(PaymentSucceededEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }

        Order order = findOrder(event.orderId());
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Order {} already processed for payment result {}", order.getId(), event.paymentId());
            return;
        }

        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            log.warn("Ignoring payment success for order {} in unexpected state {}", order.getId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        orderEventPublisher.publishOrderConfirmed(order, event.correlationId());
        log.info("Order {} marked as PAID after payment {} succeeded", order.getId(), event.paymentId());
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }

        Order order = findOrder(event.orderId());
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.CONFIRMED) {
            log.info("Ignoring payment failure for order {} in terminal state {}", order.getId(), order.getStatus());
            return;
        }

        order.setStatus(OrderStatus.FAILED);
        orderRepository.save(order);
        log.warn("Order {} marked as FAILED after payment {} failed: {}", order.getId(), event.paymentId(), event.reason());
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
