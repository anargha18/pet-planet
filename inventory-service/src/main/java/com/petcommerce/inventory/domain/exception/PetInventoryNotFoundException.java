package com.petcommerce.inventory.domain.exception;

import java.util.UUID;

/** No inventory row exists for this pet (inventory creates one when the catalog announces the pet). */
public class PetInventoryNotFoundException extends RuntimeException {

    public PetInventoryNotFoundException(UUID petId) {
        super("No inventory record for pet " + petId);
    }
}
