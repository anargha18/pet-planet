package com.petcommerce.fulfillment.consumer;

import com.petcommerce.fulfillment.event.DeliveryStatusUpdatedEvent;
import com.petcommerce.fulfillment.service.FulfillmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentEventConsumer.class);

    private final FulfillmentService fulfillmentService;

    public FulfillmentEventConsumer(FulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @KafkaListener(topics = "delivery-status-updated-events", groupId = "fulfillment-service")
    public void onDeliveryStatusUpdated(DeliveryStatusUpdatedEvent event) {
        log.info("Received DeliveryStatusUpdatedEvent for order {} with tracking {}", event.orderId(), event.trackingNumber());
        fulfillmentService.handleDeliveryStatusUpdatedEvent(event);
    }
}
