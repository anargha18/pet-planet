package com.petcommerce.payment.consumer;

import com.petcommerce.payment.event.PetReservedEvent;
import com.petcommerce.payment.service.PaymentService;
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
}
