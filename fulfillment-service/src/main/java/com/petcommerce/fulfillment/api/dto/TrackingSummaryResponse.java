package com.petcommerce.fulfillment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One order's current tracking state (list views). Statuses are null until the corresponding event arrived. */
public record TrackingSummaryResponse(
        Long orderId,
        Long customerId,
        String orderStatus,
        String paymentStatus,
        String shipmentStatus,
        String trackingNumber,
        String deliveryStatus,
        BigDecimal totalAmount,
        boolean escalated,
        String escalationReason,
        Instant createdAt,
        Instant updatedAt
) {
}
