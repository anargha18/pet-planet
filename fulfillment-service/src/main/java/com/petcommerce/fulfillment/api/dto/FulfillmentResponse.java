package com.petcommerce.fulfillment.api.dto;

import com.petcommerce.fulfillment.domain.model.FulfillmentStatus;
import java.time.Instant;
import java.util.UUID;

/** HTTP view of a fulfillment record (R-SEC-06: controllers never return JPA entities). */
public record FulfillmentResponse(
        UUID id,
        Long orderId,
        Long customerId,
        String trackingNumber,
        FulfillmentStatus status,
        Instant lastUpdatedAt,
        String notes
) {
}
