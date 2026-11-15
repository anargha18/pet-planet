package com.petcommerce.fulfillment.api.dto;

import java.time.Instant;

/** One timeline entry: what happened, reported by which service, and when it happened there. */
public record TrackingEventResponse(String eventType, String source, String description, Instant occurredAt) {
}
