package com.petcommerce.user.service;

import com.petcommerce.user.dto.ChangePasswordRequest;
import com.petcommerce.user.dto.CreateUserRequest;
import com.petcommerce.user.dto.RegisterRequest;
import com.petcommerce.user.dto.UpdateProfileRequest;
import com.petcommerce.user.dto.UpdateUserRequest;
import com.petcommerce.user.dto.UserResponse;
import com.petcommerce.user.entity.Role;
import com.petcommerce.user.entity.User;
import com.petcommerce.user.exception.LastAdminException;
import com.petcommerce.user.exception.UserAlreadyExistsException;
import com.petcommerce.user.exception.UserNotFoundException;
import com.petcommerce.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        return createUser(request.email(), request.password(), request.firstName(), request.lastName(), Role.USER);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return createUser(request.email(), request.password(), request.firstName(), request.lastName(), request.role());
    }

    @Transactional(readOnly = true)
    public User authenticate(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Integer id) {
        return toResponse(userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public UserResponse findActiveById(Integer id) {
        User user = userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new UserNotFoundException(id));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    @Transactional
    public UserResponse update(Integer id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (isLastAdminBeingRemoved(user, request.role(), request.active())) {
            throw new LastAdminException();
        }
        if (request.email() != null) {
            userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new UserAlreadyExistsException();
                    });
        }
        user.updateProfile(
                request.email() == null ? null : normalizeEmail(request.email()),
                request.firstName() == null ? null : request.firstName().trim(),
                request.lastName() == null ? null : request.lastName().trim(),
                request.role(),
                request.active()
        );
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateProfile(Integer id, UpdateProfileRequest request) {
        User user = userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new UserNotFoundException(id));
        user.updateProfile(
                normalizeEmail(request.email()),
                request.firstName().trim(),
                request.lastName().trim(),
                null,
                null
        );
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Integer id, ChangePasswordRequest request) {
        User user = userRepository.findById(id)
                .filter(User::isActive)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void deactivate(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        if (isLastAdminBeingRemoved(user, Role.USER, false)) {
            throw new LastAdminException();
        }
        user.deactivate();
        userRepository.save(user);
    }

    private UserResponse createUser(String email, String password, String firstName, String lastName, Role role) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new UserAlreadyExistsException();
        }
        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(password),
                firstName.trim(),
                lastName.trim(),
                role
        );
        return toResponse(userRepository.save(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isLastAdminBeingRemoved(User user, Role nextRole, Boolean nextActive) {
        boolean demoted = nextRole != null && nextRole != Role.ADMIN;
        boolean deactivated = Boolean.FALSE.equals(nextActive);
        return user.isActive()
                && user.getRole() == Role.ADMIN
                && (demoted || deactivated)
                && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1;
    }

    private UserResponse toResponse(User user) {
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
