package com.petcommerce.user.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(Integer id) {
        super("User not found: " + id);
    }
}
