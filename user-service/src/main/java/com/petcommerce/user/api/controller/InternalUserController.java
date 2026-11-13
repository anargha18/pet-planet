package com.petcommerce.user.api.controller;

import com.petcommerce.user.api.dto.LoginRequest;
import com.petcommerce.user.api.dto.RegisterRequest;
import com.petcommerce.user.api.dto.UserResponse;
import com.petcommerce.user.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final AuthService authService;

    public InternalUserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/authenticate")
    public UserResponse authenticate(@Valid @RequestBody LoginRequest request) {
        return authService.authenticate(request.email(), request.password());
    }
}
