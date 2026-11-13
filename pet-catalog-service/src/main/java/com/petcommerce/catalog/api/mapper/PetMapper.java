package com.petcommerce.catalog.api.mapper;

import com.petcommerce.catalog.api.dto.PetResponse;
import com.petcommerce.catalog.domain.model.Pet;

/** Converts pet entities to API response records. */
public final class PetMapper {

    private PetMapper() {
    }

    public static PetResponse toResponse(Pet pet) {
        return new PetResponse(
                pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed(), pet.getAge(),
                pet.getGender(), pet.getPrice(), pet.getDescription(), pet.getAvailabilityStatus(),
                pet.getCreatedAt(), pet.getUpdatedAt());
    }
}
