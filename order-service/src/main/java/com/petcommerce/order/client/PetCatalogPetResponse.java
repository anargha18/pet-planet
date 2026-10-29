package com.petcommerce.order.client;

import com.petcommerce.order.entity.Species;

import java.math.BigDecimal;
import java.util.UUID;

public record PetCatalogPetResponse(
        UUID id,
        String name,
        Species species,
        String breed,
        BigDecimal price,
        String availabilityStatus
) {
}
