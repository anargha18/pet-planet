package com.petcommerce.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @Email String email,
        @NotBlank String password,
        @NotBlank String firstName,
        @NotBlank String lastName
) {
}
