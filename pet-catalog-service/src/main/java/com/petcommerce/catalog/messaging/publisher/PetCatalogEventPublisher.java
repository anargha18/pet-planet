package com.petcommerce.catalog.messaging.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.catalog.domain.model.Pet;
import com.petcommerce.catalog.messaging.event.PetCatalogEvent;
import com.petcommerce.catalog.persistence.entity.CatalogOutboxEvent;
import com.petcommerce.catalog.persistence.repository.CatalogOutboxRepository;
import com.petcommerce.platform.outbox.OutboxPayloads;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes catalog events to the transactional outbox inside the caller's transaction; the platform outbox relay
 * publishes them to Kafka after commit (R-EVT-03). Previously the event was sent directly inside the transaction.
 */
@Component
public class PetCatalogEventPublisher {

    private final CatalogOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PetCatalogEventPublisher(CatalogOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishPetCreated(Pet savedPet) {
        publish(savedPet, "PetCreatedEvent");
    }

    /** Inventory retires the pet (AVAILABLE -> RETIRED). */
    public void publishPetArchived(Pet pet) {
        publish(pet, "PetArchivedEvent");
    }

    private void publish(Pet pet, String eventType) {
        if (pet.getId() == null) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        PetCatalogEvent event = new PetCatalogEvent(
                eventId,
                eventType,
                1,
                pet.getId(),
                pet.getAvailabilityStatus().name(),
                Instant.now()
        );
        outboxRepository.save(new CatalogOutboxEvent(
                eventId,
                "pet-catalog-events",
                eventType,
                pet.getId(),
                null,
                pet.getId().toString(),
                OutboxPayloads.toJson(objectMapper, event),
                Instant.now()
        ));
    }
}
