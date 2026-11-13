package com.petcommerce.user.application;

import com.petcommerce.user.api.dto.RegisterRequest;
import com.petcommerce.user.domain.model.User;
import com.petcommerce.user.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(new UserService(userRepository, passwordEncoder), userRepository, passwordEncoder);
    }

    @Test
    void registrationAlwaysCreatesRegularUser() {
        when(userRepository.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = authService.register(new RegisterRequest(
                " Person@Example.com ", "password123", "Person", "User"
        ));

        assertEquals("person@example.com", response.email());
        assertEquals("USER", response.role());
        verify(passwordEncoder).encode("password123");
    }
}
