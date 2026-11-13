package com.petcommerce.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private TestTokens tokens;
    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        tokens = new TestTokens();
        jwtService = tokens.jwtService();
    }

    @Test
    void tokenContainsIntegerUserIdAndRole() {
        JwtService.AuthenticatedUser authenticatedUser = jwtService.validate(tokens.token(42, "user@example.com", "USER"));

        assertEquals(42, authenticatedUser.id());
        assertEquals("user@example.com", authenticatedUser.email());
        assertEquals("ROLE_USER", authenticatedUser.role());
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = tokens.token(42, "user@example.com", "USER");

        assertThrows(RuntimeException.class, () -> jwtService.validate(token + "x"));
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() throws Exception {
        String foreignToken = new TestTokens().token(42, "user@example.com", "ADMIN");

        assertThrows(RuntimeException.class, () -> jwtService.validate(foreignToken));
    }
}
