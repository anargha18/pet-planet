package com.petcommerce.inventory.event;

import java.time.Instant;
import java.util.UUID;

public record PetCatalogEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID petId,
        String availabilityStatus,
        Instant timestamp
) {
}

