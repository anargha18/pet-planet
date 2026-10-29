package com.petcommerce.delivery.consumer;

import com.petcommerce.delivery.event.ShipmentCreatedEvent;
import com.petcommerce.delivery.service.DeliveryTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeliveryTrackingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryTrackingEventConsumer.class);

    private final DeliveryTrackingService deliveryTrackingService;

    public DeliveryTrackingEventConsumer(DeliveryTrackingService deliveryTrackingService) {
        this.deliveryTrackingService = deliveryTrackingService;
    }

    @KafkaListener(topics = "shipment-created-events", groupId = "delivery-tracking-service")
    public void onShipmentCreated(ShipmentCreatedEvent event) {
        log.info("Received ShipmentCreatedEvent for order {} with tracking {}", event.orderId(), event.trackingNumber());
        deliveryTrackingService.handleShipmentCreatedEvent(event);
    }
}
