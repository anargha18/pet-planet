package com.petcommerce.catalog.api.dto;

import com.petcommerce.catalog.domain.model.AvailabilityStatus;
import com.petcommerce.catalog.domain.model.Gender;
import com.petcommerce.catalog.domain.model.Species;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PetResponse(
        UUID id,
        String name,
        Species species,
        String breed,
        int age,
        Gender gender,
        BigDecimal price,
        String description,
        AvailabilityStatus availabilityStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
