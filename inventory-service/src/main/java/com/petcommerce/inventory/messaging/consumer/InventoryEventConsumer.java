package com.petcommerce.inventory.messaging.consumer;

import com.petcommerce.inventory.application.InventoryService;
import com.petcommerce.inventory.messaging.event.OrderCancelledEvent;
import com.petcommerce.inventory.messaging.event.OrderCreatedEvent;
import com.petcommerce.inventory.messaging.event.OrderFailedEvent;
import com.petcommerce.inventory.messaging.event.PaymentSucceededEvent;
import com.petcommerce.inventory.messaging.event.PetCatalogEvent;
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

    @KafkaListener(topics = "payment-succeeded-events", groupId = "inventory-service")
    public void onPaymentSucceeded(PaymentSucceededEvent event) {
        log.info("Received PaymentSucceededEvent for order {}: completing the sale", event.orderId());
        inventoryService.handlePaymentSucceededEvent(event);
    }

    @KafkaListener(topics = "order-cancelled-events", groupId = "inventory-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelledEvent for order {} ({}): releasing reserved pets", event.orderId(), event.reason());
        inventoryService.handleOrderCancelledEvent(event);
    }

    @KafkaListener(topics = "order-failed-events", groupId = "inventory-service")
    public void onOrderFailed(OrderFailedEvent event) {
        log.info("Received OrderFailedEvent for order {} ({}): releasing reserved pets", event.orderId(), event.reason());
        inventoryService.handleOrderFailedEvent(event);
    }

    @KafkaListener(topics = "pet-catalog-events", groupId = "inventory-service")
    public void onPetCatalogEvent(PetCatalogEvent event) {
        log.info("Received {} for pet {}", event.eventType(), event.petId());
        inventoryService.handlePetCatalogEvent(event);
    }
}
