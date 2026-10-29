package com.petcommerce.inventory.event;

import java.time.Instant;
import java.util.UUID;

public record InventoryStatusChangedEvent(
        UUID eventId,
        String eventType,
        Integer eventVersion,
        UUID petId,
        String status,
        Long orderId,
        Instant timestamp
) {
}

