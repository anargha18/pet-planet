package com.petcommerce.order.dto;

import com.petcommerce.order.entity.Species;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        Long id,
        UUID petId,
        String petName,
        Species petSpecies,
        String petBreed,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {
}
