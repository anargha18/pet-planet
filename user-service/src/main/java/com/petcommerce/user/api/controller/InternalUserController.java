package com.petcommerce.user.api.controller;

import com.petcommerce.user.api.dto.LoginRequest;
import com.petcommerce.user.api.dto.RegisterRequest;
import com.petcommerce.user.api.dto.UserResponse;
import com.petcommerce.user.domain.model.User;
import com.petcommerce.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/authenticate")
    public UserResponse authenticate(@Valid @RequestBody LoginRequest request) {
        User user = userService.authenticate(request.email(), request.password());
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
