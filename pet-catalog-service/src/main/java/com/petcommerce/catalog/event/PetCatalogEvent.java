package com.petcommerce.catalog.event;

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

