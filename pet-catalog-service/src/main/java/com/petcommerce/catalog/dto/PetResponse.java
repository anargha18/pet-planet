package com.petcommerce.catalog.dto;

import com.petcommerce.catalog.entity.AvailabilityStatus;
import com.petcommerce.catalog.entity.Gender;
import com.petcommerce.catalog.entity.Species;

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
