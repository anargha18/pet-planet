package com.petcommerce.catalog.domain.exception;

import java.util.UUID;

/** Reserved or sold pets cannot be archived: an order depends on them. */
public class PetNotArchivableException extends RuntimeException {
    public PetNotArchivableException(UUID petId, String status) {
        super("Pet " + petId + " cannot be archived because it is " + status);
    }
}
