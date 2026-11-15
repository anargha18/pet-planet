package com.petcommerce.user.api.mapper;

import com.petcommerce.user.api.dto.UserResponse;
import com.petcommerce.user.domain.model.User;

/** Converts user entities to API response records (never exposes the password hash). */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().name(),
                user.isActive(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
