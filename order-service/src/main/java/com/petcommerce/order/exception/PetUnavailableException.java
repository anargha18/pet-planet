package com.petcommerce.order.exception;

import java.util.UUID;

public class PetUnavailableException extends RuntimeException {
    public PetUnavailableException(UUID petId) {
        super("Pet is not available for purchase: " + petId);
    }
}
