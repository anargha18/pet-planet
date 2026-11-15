package com.petcommerce.user.domain.exception;

public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("The last active admin cannot be deactivated or demoted");
    }
}
