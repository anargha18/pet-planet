package com.petcommerce.user.application;

import com.petcommerce.user.api.dto.AuthResponse;
import com.petcommerce.user.api.dto.RegisterRequest;
import com.petcommerce.user.api.dto.UserResponse;
import com.petcommerce.user.domain.model.Role;
import com.petcommerce.user.domain.model.User;
import com.petcommerce.user.domain.policy.EmailPolicy;
import com.petcommerce.user.persistence.repository.UserRepository;
import com.petcommerce.user.security.token.TokenIssuer;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication use cases: self-registration and login, both returning a signed access token.
 * Account management (profile, admin CRUD) lives in {@link UserService}.
 */
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;

    public AuthService(UserService userService, UserRepository userRepository,
                       PasswordEncoder passwordEncoder, TokenIssuer tokenIssuer) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
    }

    /** Public self-registration always creates a regular USER account and logs it in. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        UserResponse user = userService.createAccount(
                request.email(), request.password(), request.firstName(), request.lastName(), Role.USER);
        return authResponse(user.id(), user.email(), user.role());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(EmailPolicy.normalize(email))
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        return authResponse(user.getId(), user.getEmail(), user.getRole().name());
    }

    private AuthResponse authResponse(Integer id, String email, String role) {
        return new AuthResponse(tokenIssuer.issue(id, email, role), TOKEN_TYPE, id, email, role);
    }
}
