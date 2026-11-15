package com.petcommerce.fulfillment.api.mapper;

import com.petcommerce.fulfillment.api.dto.TrackingEventResponse;
import com.petcommerce.fulfillment.api.dto.TrackingResponse;
import com.petcommerce.fulfillment.api.dto.TrackingSummaryResponse;
import com.petcommerce.fulfillment.domain.model.OrderTracking;
import com.petcommerce.fulfillment.domain.model.OrderTrackingEvent;

import java.util.List;

public final class TrackingMapper {

    private TrackingMapper() {
    }

    public static TrackingSummaryResponse toSummary(OrderTracking t) {
        return new TrackingSummaryResponse(
                t.getOrderId(),
                t.getCustomerId(),
                name(t.getOrderStatus()),
                name(t.getPaymentStatus()),
                name(t.getShipmentStatus()),
                t.getTrackingNumber(),
                name(t.getDeliveryStatus()),
                t.getTotalAmount(),
                t.isEscalated(),
                name(t.getEscalationReason()),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }

    public static TrackingResponse toResponse(OrderTracking t, List<OrderTrackingEvent> timeline) {
        return new TrackingResponse(toSummary(t), timeline.stream()
                .map(e -> new TrackingEventResponse(e.getEventType(), e.getSource(), e.getDescription(), e.getOccurredAt()))
                .toList());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
