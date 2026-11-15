package com.petcommerce.shipping.api.dto;

import com.petcommerce.shipping.domain.model.ShipmentStatus;
import java.time.Instant;
import java.util.UUID;

/** HTTP view of a shipment (R-SEC-06: controllers never return JPA entities). */
public record ShipmentResponse(
        UUID id,
        Long orderId,
        Long customerId,
        ShipmentStatus status,
        String trackingNumber,
        Instant createdAt,
        Instant shippedAt,
        Instant deliveredAt
) {
}
