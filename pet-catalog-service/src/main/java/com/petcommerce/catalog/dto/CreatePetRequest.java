package com.petcommerce.catalog.dto;

import com.petcommerce.catalog.entity.Gender;
import com.petcommerce.catalog.entity.Species;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreatePetRequest(
        @NotBlank String name,
        @NotNull Species species,
        @NotBlank String breed,
        @Min(0) int age,
        @NotNull Gender gender,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        @NotBlank String description
) {
}
