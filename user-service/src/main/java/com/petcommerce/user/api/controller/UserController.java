package com.petcommerce.user.api.controller;

import com.petcommerce.user.api.dto.ChangePasswordRequest;
import com.petcommerce.user.api.dto.CreateUserRequest;
import com.petcommerce.user.api.dto.UpdateProfileRequest;
import com.petcommerce.user.api.dto.UpdateUserRequest;
import com.petcommerce.user.api.dto.UserResponse;
import com.petcommerce.user.application.UserService;
import com.petcommerce.platform.security.AuthenticatedCaller;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Self-service profile ({@code /me}, any valid token; the user ID comes from the verified JWT) and user
 * administration (ROLE_ADMIN, enforced in {@code security.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(AuthenticatedCaller caller) {
        return userService.findActiveById(userIdOf(caller));
    }

    @PatchMapping("/me")
    public UserResponse updateMe(
            AuthenticatedCaller caller,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateProfile(userIdOf(caller), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMyPassword(
            AuthenticatedCaller caller,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(userIdOf(caller), request);
    }

    @GetMapping
    public Page<UserResponse> getUsers(Pageable pageable) {
        return userService.findAll(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Integer id) {
        return userService.findById(id);
    }

    @PatchMapping("/{id}")
    public UserResponse updateUser(@PathVariable Integer id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Integer id) {
        userService.deactivate(id);
    }

    private static Integer userIdOf(AuthenticatedCaller caller) {
        return Math.toIntExact(caller.userId());
    }
}
