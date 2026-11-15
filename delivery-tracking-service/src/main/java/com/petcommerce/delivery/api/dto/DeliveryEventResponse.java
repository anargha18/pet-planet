package com.petcommerce.delivery.api.dto;

import com.petcommerce.delivery.domain.model.DeliveryStatus;

import java.time.Instant;

/** One timeline entry of a delivery. */
public record DeliveryEventResponse(DeliveryStatus status, String note, Instant occurredAt) {
}
