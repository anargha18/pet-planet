package com.petcommerce.inventory.messaging.consumer;

import com.petcommerce.inventory.messaging.event.OrderCancelledEvent;
import com.petcommerce.inventory.messaging.event.OrderConfirmedEvent;
import com.petcommerce.inventory.messaging.event.OrderCreatedEvent;
import com.petcommerce.inventory.messaging.event.PaymentFailedEvent;
import com.petcommerce.inventory.messaging.event.PetCatalogEvent;
import com.petcommerce.inventory.application.InventoryService;
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

    @KafkaListener(topics = "payment-failed-events", groupId = "inventory-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.warn("Received PaymentFailedEvent for order {} — releasing reserved pets", event.orderId());
        inventoryService.handlePaymentFailedEvent(event);
    }

    @KafkaListener(topics = "order-cancelled-events", groupId = "inventory-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Received OrderCancelledEvent for order {} — releasing reserved pets", event.orderId());
        inventoryService.handleOrderCancelledEvent(event);
    }

    @KafkaListener(topics = "pet-catalog-events", groupId = "inventory-service")
    public void onPetCatalogEvent(PetCatalogEvent event) {
        log.info("Received PetCatalogEvent for pet {}", event.petId());
        inventoryService.handlePetCatalogEvent(event);
    }
}
