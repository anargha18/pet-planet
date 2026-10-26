package com.petcommerce.order.consumer;

import com.petcommerce.order.event.PaymentFailedEvent;
import com.petcommerce.order.event.PaymentSucceededEvent;
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
