package com.petcommerce.user.api.dto;

/** Login/registration result: the signed access token plus the caller's identity. */
public record AuthResponse(
        String token,
        String tokenType,
        Integer id,
        String email,
        String role
) {
}
