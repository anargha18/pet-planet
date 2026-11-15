package com.petcommerce.delivery.api.mapper;

import com.petcommerce.delivery.api.dto.DeliveryEventResponse;
import com.petcommerce.delivery.api.dto.DeliveryResponse;
import com.petcommerce.delivery.domain.model.DeliveryEvent;
import com.petcommerce.delivery.domain.model.DeliveryRecord;

import java.util.List;

public final class DeliveryRecordMapper {

    private DeliveryRecordMapper() {
    }

    public static DeliveryResponse toResponse(DeliveryRecord d, List<DeliveryEvent> timeline) {
        return new DeliveryResponse(
                d.getId(),
                d.getOrderId(),
                d.getCustomerId(),
                d.getTrackingNumber(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getDeliveredAt(),
                timeline.stream()
                        .map(e -> new DeliveryEventResponse(e.getStatus(), e.getNote(), e.getOccurredAt()))
                        .toList()
        );
    }
}
