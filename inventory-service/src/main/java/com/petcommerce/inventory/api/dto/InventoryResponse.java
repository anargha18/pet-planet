package com.petcommerce.inventory.api.dto;

import java.time.Instant;
import java.util.UUID;

public record InventoryResponse(
        UUID petId,
        String status,
        Long orderId,
        UUID reservationId,
        Instant reservedAt,
        Instant reservationExpiresAt,
        Instant soldAt,
        long version,
        Instant updatedAt
) {
}
