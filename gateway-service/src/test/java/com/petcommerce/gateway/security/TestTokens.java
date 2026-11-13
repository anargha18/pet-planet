package com.petcommerce.gateway.security;

import io.jsonwebtoken.Jwts;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Test-only token signer producing the same claims as user-service's TokenIssuer
 * (the gateway itself never signs tokens).
 */
final class TestTokens {

    private final KeyPair keyPair;

    TestTokens() throws Exception {
        this.keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    /** A JwtService configured to trust this signer's public key. */
    JwtService jwtService() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        ReflectionTestUtils.setField(jwtService, "issuer", "pet-planet");
        ReflectionTestUtils.setField(jwtService, "audience", "pet-planet");
        return jwtService;
    }

    String token(int userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .issuer("pet-planet")
                .audience().add("pet-planet").and()
                .claims(Map.of("userId", userId, "role", "ROLE_" + role))
                .subject(email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000L))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
