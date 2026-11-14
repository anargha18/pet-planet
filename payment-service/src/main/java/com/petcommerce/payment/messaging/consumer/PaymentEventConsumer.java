package com.petcommerce.payment.messaging.consumer;

import com.petcommerce.payment.messaging.event.OrderCancelledEvent;
import com.petcommerce.payment.messaging.event.OrderFailedEvent;
import com.petcommerce.payment.messaging.event.PetReservedEvent;
import com.petcommerce.payment.application.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final PaymentService paymentService;

    public PaymentEventConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = "pet-reserved-events", groupId = "payment-service")
    public void onPetReserved(PetReservedEvent event) {
        log.info("Received PetReservedEvent for order {} with reservation {}", event.orderId(), event.reservationId());
        paymentService.handlePetReservedEvent(event);
    }

    @KafkaListener(topics = "order-cancelled-events", groupId = "payment-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelledEvent for order {} ({})", event.orderId(), event.reason());
        paymentService.handleOrderClosed(event.eventId(), event.orderId(), event.correlationId(), "ORDER_CANCELLED:" + event.reason());
    }

    @KafkaListener(topics = "order-failed-events", groupId = "payment-service")
    public void onOrderFailed(OrderFailedEvent event) {
        log.info("Received OrderFailedEvent for order {} ({})", event.orderId(), event.reason());
        paymentService.handleOrderClosed(event.eventId(), event.orderId(), event.correlationId(), "ORDER_FAILED:" + event.reason());
    }
}
