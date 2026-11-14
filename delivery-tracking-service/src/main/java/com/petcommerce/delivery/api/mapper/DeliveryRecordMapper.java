package com.petcommerce.delivery.api.mapper;

import com.petcommerce.delivery.api.dto.DeliveryResponse;
import com.petcommerce.delivery.domain.model.DeliveryRecord;

public final class DeliveryRecordMapper {

    private DeliveryRecordMapper() {
    }

    public static DeliveryResponse toResponse(DeliveryRecord d) {
        return new DeliveryResponse(
                d.getId(),
                d.getOrderId(),
                d.getCustomerId(),
                d.getTrackingNumber(),
                d.getStatus(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                d.getDeliveredAt()
        );
    }
}
