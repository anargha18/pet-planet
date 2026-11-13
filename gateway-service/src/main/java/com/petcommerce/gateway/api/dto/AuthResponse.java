package com.petcommerce.gateway.api.dto;

public record AuthResponse(
        String token,
        String tokenType,
        Integer id,
        String email,
        String role
) {
}
