package com.petcommerce.fulfillment.api.dto;

import java.util.List;

/** One order's tracking state plus its full timeline, oldest first (R-SVC-01). */
public record TrackingResponse(TrackingSummaryResponse summary, List<TrackingEventResponse> timeline) {
}
