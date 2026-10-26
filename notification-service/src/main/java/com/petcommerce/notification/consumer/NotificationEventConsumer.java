package com.petcommerce.notification.consumer;

import com.petcommerce.notification.event.OrderConfirmedEvent;
import com.petcommerce.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationService notificationService;

    public NotificationEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "order-confirmed-events", groupId = "notification-service")
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Received OrderConfirmedEvent for order {} with {} pets", event.orderId(), event.petIds().size());
        notificationService.handleOrderConfirmedEvent(event);
    }
}
