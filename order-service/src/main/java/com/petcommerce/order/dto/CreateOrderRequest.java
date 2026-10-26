package com.petcommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "customerId is required") Long customerId,
        @NotEmpty(message = "items must not be empty") @Valid List<CreateOrderItemRequest> items
) {
}
