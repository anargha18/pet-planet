package com.petcommerce.catalog.messaging.event;

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

