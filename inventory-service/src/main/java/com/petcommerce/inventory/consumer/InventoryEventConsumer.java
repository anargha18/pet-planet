package com.petcommerce.inventory.consumer;

import com.petcommerce.inventory.event.OrderConfirmedEvent;
import com.petcommerce.inventory.event.OrderCreatedEvent;
import com.petcommerce.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventConsumer.class);

    private final InventoryService inventoryService;

    public InventoryEventConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = "order-created-events", groupId = "inventory-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order {} with {} pets", event.orderId(), event.petIds().size());
        inventoryService.handleOrderCreatedEvent(event);
    }

    @KafkaListener(topics = "order-confirmed-events", groupId = "inventory-service")
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        log.info("Received OrderConfirmedEvent for order {} with {} pets", event.orderId(), event.petIds().size());
        inventoryService.handleOrderConfirmedEvent(event);
    }
}
