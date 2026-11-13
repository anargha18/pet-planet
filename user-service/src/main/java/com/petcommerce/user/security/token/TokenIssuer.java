package com.petcommerce.user.security.token;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Issues RS256 access tokens. user-service is the only service holding the private key;
 * the gateway and other services verify tokens with the public key.
 *
 * Claims: sub = email, userId = integer user ID, role = ROLE_USER / ROLE_ADMIN, iss, aud, iat, exp.
 */
@Component
public class TokenIssuer {

    private final PrivateKey signingKey;
    private final long expirationMs;
    private final String issuer;
    private final String audience;

    public TokenIssuer(
            @Value("${jwt.private-key:}") String privateKey,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs,
            @Value("${jwt.issuer:pet-planet}") String issuer,
            @Value("${jwt.audience:pet-planet}") String audience
    ) {
        this.signingKey = parsePrivateKey(privateKey);
        this.expirationMs = expirationMs;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issue(Integer userId, String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .claims(Map.of("userId", userId, "role", normalizeRole(role)))
                .subject(email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();
    }

    private static PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Missing JWT private key configuration (JWT_PRIVATE_KEY)");
        }
        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT private key is not a valid PKCS#8 RSA key", ex);
        }
    }

    private static String normalizeRole(String role) {
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }
}
