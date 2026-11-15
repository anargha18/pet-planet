package com.petcommerce.delivery.api.dto;

import com.petcommerce.delivery.domain.model.DeliveryStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin request to advance a delivery (R-SAGA-06): {@code OUT_FOR_DELIVERY}, {@code DELIVERED} or {@code DELIVERY_FAILED}. */
public record UpdateDeliveryStatusRequest(
        @NotNull DeliveryStatus status,
        @Size(max = 500) String note
) {
}
