package com.petcommerce.order.messaging.consumer;

import com.petcommerce.order.messaging.event.PaymentFailedEvent;
import com.petcommerce.order.messaging.event.PaymentSucceededEvent;
import com.petcommerce.order.messaging.event.PetReservationExpiredEvent;
import com.petcommerce.order.messaging.event.PetReservationFailedEvent;
import com.petcommerce.order.messaging.event.PetReservedEvent;
import com.petcommerce.order.application.OrderSagaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderSagaService orderSagaService;

    public OrderEventConsumer(OrderSagaService orderSagaService) {
        this.orderSagaService = orderSagaService;
    }

    @KafkaListener(topics = "pet-reserved-events", groupId = "order-service")
    public void onPetReserved(PetReservedEvent event) {
        log.info("Received PetReservedEvent for order {} with reservation {}", event.orderId(), event.reservationId());
        orderSagaService.handlePetReserved(event);
    }

    @KafkaListener(topics = "pet-reservation-failed-events", groupId = "order-service")
    public void onPetReservationFailed(PetReservationFailedEvent event) {
        log.warn("Received PetReservationFailedEvent for order {} pet {} reason {}", event.orderId(), event.petId(), event.reason());
        orderSagaService.handlePetReservationFailed(event);
    }

    @KafkaListener(topics = "pet-reservation-expired-events", groupId = "order-service")
    public void onPetReservationExpired(PetReservationExpiredEvent event) {
        log.warn("Received PetReservationExpiredEvent for order {} pet {}", event.orderId(), event.petId());
        orderSagaService.handlePetReservationExpired(event);
    }

    @KafkaListener(topics = "payment-succeeded-events", groupId = "order-service")
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Received PaymentSucceededEvent for order {} with payment {}", event.orderId(), event.paymentId());
        orderSagaService.handlePaymentSucceeded(event);
    }

    @KafkaListener(topics = "payment-failed-events", groupId = "order-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent for order {} with payment {} reason {}", event.orderId(), event.paymentId(), event.reason());
        orderSagaService.handlePaymentFailed(event);
    }
}
