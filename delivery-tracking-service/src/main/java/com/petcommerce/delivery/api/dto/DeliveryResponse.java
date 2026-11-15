package com.petcommerce.delivery.api.dto;

import com.petcommerce.delivery.domain.model.DeliveryStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** HTTP view of a delivery with its timeline, oldest first (R-SEC-06: controllers never return JPA entities). */
public record DeliveryResponse(
        UUID id,
        Long orderId,
        Long customerId,
        String trackingNumber,
        DeliveryStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant deliveredAt,
        List<DeliveryEventResponse> timeline
) {
}
