package com.petcommerce.user.application;

import com.petcommerce.user.api.dto.RegisterRequest;
import com.petcommerce.user.api.dto.UserResponse;
import com.petcommerce.user.api.mapper.UserMapper;
import com.petcommerce.user.domain.model.Role;
import com.petcommerce.user.domain.model.User;
import com.petcommerce.user.domain.policy.EmailPolicy;
import com.petcommerce.user.persistence.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use cases: self-registration and credential verification.
 * Account management (profile, admin CRUD) lives in {@link UserService}.
 */
@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserService userService, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Public self-registration always creates a regular USER account. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        return userService.createAccount(request.email(), request.password(), request.firstName(), request.lastName(), Role.USER);
    }

    @Transactional(readOnly = true)
    public UserResponse authenticate(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(EmailPolicy.normalize(email))
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return UserMapper.toResponse(user);
    }
}
