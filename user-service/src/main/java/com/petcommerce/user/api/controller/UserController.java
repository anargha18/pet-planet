package com.petcommerce.user.api.controller;

import com.petcommerce.user.api.dto.ChangePasswordRequest;
import com.petcommerce.user.api.dto.CreateUserRequest;
import com.petcommerce.user.api.dto.UpdateProfileRequest;
import com.petcommerce.user.api.dto.UpdateUserRequest;
import com.petcommerce.user.api.dto.UserResponse;
import com.petcommerce.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(@RequestHeader("X-Authenticated-User-Id") Integer userId) {
        return userService.findActiveById(userId);
    }

    @PatchMapping("/me")
    public UserResponse updateMe(
            @RequestHeader("X-Authenticated-User-Id") Integer userId,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateProfile(userId, request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeMyPassword(
            @RequestHeader("X-Authenticated-User-Id") Integer userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(userId, request);
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
}
