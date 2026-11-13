package com.petcommerce.order.domain.exception;

import java.util.UUID;

public class PetNotFoundException extends RuntimeException {
    public PetNotFoundException(UUID petId) {
        super("Pet not found with id: " + petId);
    }
}
