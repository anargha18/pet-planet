package com.petcommerce.user.exception;

public class LastAdminException extends RuntimeException {
    public LastAdminException() {
        super("The last active admin cannot be deactivated or demoted");
    }
}
