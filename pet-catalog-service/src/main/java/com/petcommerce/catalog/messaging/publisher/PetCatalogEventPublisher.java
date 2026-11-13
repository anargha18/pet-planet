package com.petcommerce.catalog.messaging.publisher;

import com.petcommerce.catalog.domain.model.Pet;
import com.petcommerce.catalog.messaging.event.PetCatalogEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes catalog events to Kafka.
 * Note: sends directly (no outbox yet) — moving this to a transactional outbox is planned (R-SAGA-05).
 */
@Component
public class PetCatalogEventPublisher {

    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired(required = false)
    public void setKafkaTemplate(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPetCreated(Pet savedPet) {
        if (kafkaTemplate != null && savedPet.getId() != null) {
            kafkaTemplate.send("pet-catalog-events", savedPet.getId().toString(), new PetCatalogEvent(
                    UUID.randomUUID(),
                    "PetCreatedEvent",
                    1,
                    savedPet.getId(),
                    savedPet.getAvailabilityStatus().name(),
                    Instant.now()
            ));
        }
    }
}
