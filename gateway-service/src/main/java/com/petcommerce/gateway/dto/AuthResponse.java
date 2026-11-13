package com.petcommerce.gateway.dto;

public record AuthResponse(
        String token,
        String tokenType,
        Integer id,
        String email,
        String role
) {
}
