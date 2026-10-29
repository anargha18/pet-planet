package com.petcommerce.shipping.consumer;

import com.petcommerce.shipping.event.OrderConfirmedEvent;
import com.petcommerce.shipping.service.ShippingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ShippingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ShippingEventConsumer.class);

    private final ShippingService shippingService;

    public ShippingEventConsumer(ShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @KafkaListener(topics = "order-confirmed-events", groupId = "shipping-service")
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Received OrderConfirmedEvent for order {} with {} pets", event.orderId(), event.petIds().size());
        shippingService.handleOrderConfirmedEvent(event);
    }
}
