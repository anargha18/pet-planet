package com.petcommerce.order.domain.exception;

public class PetCatalogUnavailableException extends RuntimeException {
    public PetCatalogUnavailableException(String message) {
        super(message);
    }
}
