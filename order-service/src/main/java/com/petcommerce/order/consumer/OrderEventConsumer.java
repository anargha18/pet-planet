package com.petcommerce.order.consumer;

import com.petcommerce.order.event.PaymentFailedEvent;
import com.petcommerce.order.event.PaymentSucceededEvent;
import com.petcommerce.order.event.PetReservationExpiredEvent;
import com.petcommerce.order.event.PetReservationFailedEvent;
import com.petcommerce.order.event.PetReservedEvent;
import com.petcommerce.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderService orderService;

    public OrderEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "pet-reserved-events", groupId = "order-service")
    public void onPetReserved(PetReservedEvent event) {
        log.info("Received PetReservedEvent for order {} with reservation {}", event.orderId(), event.reservationId());
        orderService.handlePetReserved(event);
    }

    @KafkaListener(topics = "pet-reservation-failed-events", groupId = "order-service")
    public void onPetReservationFailed(PetReservationFailedEvent event) {
        log.warn("Received PetReservationFailedEvent for order {} pet {} reason {}", event.orderId(), event.petId(), event.reason());
        orderService.handlePetReservationFailed(event);
    }

    @KafkaListener(topics = "pet-reservation-expired-events", groupId = "order-service")
    public void onPetReservationExpired(PetReservationExpiredEvent event) {
        log.warn("Received PetReservationExpiredEvent for order {} pet {}", event.orderId(), event.petId());
        orderService.handlePetReservationExpired(event);
    }

    @KafkaListener(topics = "payment-succeeded-events", groupId = "order-service")
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Received PaymentSucceededEvent for order {} with payment {}", event.orderId(), event.paymentId());
        orderService.handlePaymentSucceeded(event);
    }

    @KafkaListener(topics = "payment-failed-events", groupId = "order-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent for order {} with payment {} reason {}", event.orderId(), event.paymentId(), event.reason());
        orderService.handlePaymentFailed(event);
    }
}
