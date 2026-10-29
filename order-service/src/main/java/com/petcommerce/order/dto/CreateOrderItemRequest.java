package com.petcommerce.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateOrderItemRequest(
        @NotNull(message = "petId is required") UUID petId,
        @Min(value = 1, message = "quantity must be at least 1") Integer quantity
) {
}
