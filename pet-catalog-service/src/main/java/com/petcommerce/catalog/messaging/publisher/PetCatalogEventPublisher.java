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
        if (savedPet.getId() == null) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        PetCatalogEvent event = new PetCatalogEvent(
                eventId,
                "PetCreatedEvent",
                1,
                savedPet.getId(),
                savedPet.getAvailabilityStatus().name(),
                Instant.now()
        );
        outboxRepository.save(new CatalogOutboxEvent(
                eventId,
                "pet-catalog-events",
                "PetCreatedEvent",
                savedPet.getId(),
                null,
                savedPet.getId().toString(),
                OutboxPayloads.toJson(objectMapper, event),
                Instant.now()
        ));
    }
}
