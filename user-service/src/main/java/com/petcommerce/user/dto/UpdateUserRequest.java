package com.petcommerce.user.dto;

import com.petcommerce.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Email @Size(max = 255) String email,
        @Pattern(regexp = ".*\\S.*") @Size(max = 100) String firstName,
        @Pattern(regexp = ".*\\S.*") @Size(max = 100) String lastName,
        Role role,
        Boolean active
) {
}
