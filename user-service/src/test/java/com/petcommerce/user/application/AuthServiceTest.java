package com.petcommerce.user.application;

import com.petcommerce.user.api.dto.RegisterRequest;
import com.petcommerce.user.domain.model.User;
import com.petcommerce.user.persistence.repository.UserRepository;
import com.petcommerce.user.security.token.TokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void setUp() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        TokenIssuer tokenIssuer = new TokenIssuer(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()), 3_600_000L, "pet-planet", "pet-planet");
        authService = new AuthService(new UserService(userRepository, passwordEncoder), userRepository, passwordEncoder, tokenIssuer);
    }

    @Test
    void registrationAlwaysCreatesRegularUser() {
        when(userRepository.findByEmailIgnoreCase("person@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1); // the database assigns the identity in production
            return saved;
        });

        var response = authService.register(new RegisterRequest(
                " Person@Example.com ", "password123", "Person", "User"
        ));

        assertEquals("person@example.com", response.email());
        assertEquals("USER", response.role());
        assertNotNull(response.token());
        assertEquals("Bearer", response.tokenType());
        verify(passwordEncoder).encode("password123");
    }
}
