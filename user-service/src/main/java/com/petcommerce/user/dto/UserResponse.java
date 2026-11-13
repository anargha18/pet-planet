package com.petcommerce.user.dto;

import java.time.Instant;

public record UserResponse(
        Integer id,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
