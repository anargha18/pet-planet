package com.petcommerce.shipping.api.mapper;

import com.petcommerce.shipping.api.dto.ShipmentResponse;
import com.petcommerce.shipping.domain.model.Shipment;

public final class ShipmentMapper {

    private ShipmentMapper() {
    }

    public static ShipmentResponse toResponse(Shipment s) {
        return new ShipmentResponse(
                s.getId(),
                s.getOrderId(),
                s.getCustomerId(),
                s.getStatus(),
                s.getTrackingNumber(),
                s.getCreatedAt(),
                s.getShippedAt(),
                s.getDeliveredAt()
        );
    }
}
