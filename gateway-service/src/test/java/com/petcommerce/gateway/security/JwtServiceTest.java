package com.petcommerce.gateway.security;

import com.petcommerce.gateway.api.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "privateKey", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        ReflectionTestUtils.setField(jwtService, "publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "issuer", "pet-planet");
        ReflectionTestUtils.setField(jwtService, "audience", "pet-planet");
    }

    @Test
    void tokenContainsIntegerUserIdAndRole() {
        UserResponse user = new UserResponse(42, "user@example.com", "Test", "User", "USER", true, null, null);

        JwtService.AuthenticatedUser authenticatedUser = jwtService.validate(jwtService.generateToken(user));

        assertEquals(42, authenticatedUser.id());
        assertEquals("user@example.com", authenticatedUser.email());
        assertEquals("ROLE_USER", authenticatedUser.role());
    }

    @Test
    void tamperedTokenIsRejected() {
        UserResponse user = new UserResponse(42, "user@example.com", "Test", "User", "USER", true, null, null);
        String token = jwtService.generateToken(user);

        assertThrows(RuntimeException.class, () -> jwtService.validate(token + "x"));
    }
}
