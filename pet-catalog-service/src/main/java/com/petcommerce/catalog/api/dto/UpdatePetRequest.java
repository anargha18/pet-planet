package com.petcommerce.catalog.api.dto;

import com.petcommerce.catalog.domain.model.AvailabilityStatus;
import com.petcommerce.catalog.domain.model.Gender;
import com.petcommerce.catalog.domain.model.Species;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdatePetRequest(
        @NotBlank String name,
        @NotNull Species species,
        @NotBlank String breed,
        @Min(0) int age,
        @NotNull Gender gender,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        @NotBlank String description,
        @NotNull AvailabilityStatus availabilityStatus
) {
}
