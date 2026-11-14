package com.petcommerce.fulfillment.api.mapper;

import com.petcommerce.fulfillment.api.dto.FulfillmentResponse;
import com.petcommerce.fulfillment.domain.model.FulfillmentRecord;

public final class FulfillmentRecordMapper {

    private FulfillmentRecordMapper() {
    }

    public static FulfillmentResponse toResponse(FulfillmentRecord f) {
        return new FulfillmentResponse(
                f.getId(),
                f.getOrderId(),
                f.getCustomerId(),
                f.getTrackingNumber(),
                f.getStatus(),
                f.getLastUpdatedAt(),
                f.getNotes()
        );
    }
}
