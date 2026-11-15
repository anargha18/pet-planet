package com.petcommerce.shipping.messaging.consumer;

import com.petcommerce.shipping.application.ShippingService;
import com.petcommerce.shipping.messaging.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.shipping.messaging.event.OrderConfirmedEvent;
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
        log.info("Received OrderConfirmedEvent for order {}", event.orderId());
        shippingService.handleOrderConfirmedEvent(event);
    }

    /** Shipment status mirrors carrier progress (R-SVC-03, R-SAGA-06). */
    @KafkaListener(topics = "delivery-status-updated-events", groupId = "shipping-service")
    public void onDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        log.info("Received DeliveryStatusUpdatedEvent for order {}: {}", event.orderId(), event.status());
        shippingService.handleDeliveryStatusUpdatedEvent(event);
    }
}
