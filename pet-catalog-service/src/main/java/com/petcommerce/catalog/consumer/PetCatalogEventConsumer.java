package com.petcommerce.catalog.consumer;

import com.petcommerce.catalog.event.InventoryStatusChangedEvent;
import com.petcommerce.catalog.service.PetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PetCatalogEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PetCatalogEventConsumer.class);

    private final PetService petService;

    public PetCatalogEventConsumer(PetService petService) {
        this.petService = petService;
    }

    @KafkaListener(topics = "inventory-status-events", groupId = "pet-catalog-service")
    public void onInventoryStatusChanged(InventoryStatusChangedEvent event) {
        if (event == null || event.petId() == null) {
            return;
        }
        log.info("Received InventoryStatusChangedEvent for pet {} with status {}", event.petId(), event.status());
        petService.syncAvailabilityStatus(event.petId(), event.status());
    }
}

