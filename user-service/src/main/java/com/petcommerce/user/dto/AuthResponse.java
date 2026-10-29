package com.petcommerce.user.dto;

public record AuthResponse(
        String token,
        String tokenType,
        String email,
        String role
) {
}
