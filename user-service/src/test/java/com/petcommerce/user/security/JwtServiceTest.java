package com.petcommerce.user.security;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private JwtService jwtService;
    private PrivateKey privateKey;
    private final String issuer = "pet-planet";
    private final String audience = "pet-planet";

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtPrivateKey", toPrivatePem(privateKey));
        ReflectionTestUtils.setField(jwtService, "jwtPublicKey", toPublicPem(publicKey));
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3600000L);
        ReflectionTestUtils.setField(jwtService, "issuer", issuer);
        ReflectionTestUtils.setField(jwtService, "audience", audience);
    }

    @Test
    void validTokenPassesValidation() {
        String token = jwtService.generateToken("customer@example.com", "ADMIN");

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("customer@example.com", jwtService.extractEmail(token));
        assertEquals("ROLE_ADMIN", jwtService.extractRole(token));
    }

    @Test
    void tamperedTokenFailsValidation() {
        String token = jwtService.generateToken("customer@example.com", "CUSTOMER");

        assertFalse(jwtService.isTokenValid(token + "x"));
    }

    @Test
    void expiredTokenFailsValidation() {
        String token = Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .claims(Map.of("role", "ROLE_CUSTOMER"))
                .subject("customer@example.com")
                .issuedAt(new Date(System.currentTimeMillis() - 60000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        assertFalse(jwtService.isTokenValid(token));
    }

    private String toPrivatePem(PrivateKey key) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(key.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }

    private String toPublicPem(PublicKey key) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }
}
