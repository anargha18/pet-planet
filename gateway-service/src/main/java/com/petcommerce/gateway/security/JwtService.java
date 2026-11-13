package com.petcommerce.gateway.security;

import com.petcommerce.gateway.api.dto.UserResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Set;

@Service
public class JwtService {

    @Value("${jwt.private-key:}")
    private String privateKey;

    @Value("${jwt.public-key:}")
    private String publicKey;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Value("${jwt.issuer:pet-planet}")
    private String issuer;

    @Value("${jwt.audience:pet-planet}")
    private String audience;

    public String generateToken(UserResponse user) {
        Date now = new Date();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .claims(Map.of("userId", user.id(), "role", normalizeRole(user.role())))
                .subject(user.email())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(getSigningKey(), Jwts.SIG.RS256)
                .compact();
    }

    public AuthenticatedUser validate(String token) {
        Claims claims = Jwts.parser()
                .requireIssuer(issuer)
                .requireAudience(audience)
                .verifyWith(getVerificationKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        Integer userId = claims.get("userId", Integer.class);
        String email = claims.getSubject();
        String role = normalizeRole(claims.get("role", String.class));
        if (userId == null || userId <= 0 || email == null || email.isBlank()
                || role == null || !Set.of("ROLE_USER", "ROLE_ADMIN").contains(role)) {
            throw new IllegalArgumentException("Invalid authentication token claims");
        }
        return new AuthenticatedUser(userId, email, role);
    }

    private PrivateKey getSigningKey() {
        return parsePrivateKey(privateKey);
    }

    private PublicKey getVerificationKey() {
        return parsePublicKey(publicKey);
    }

    private PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Missing JWT private key configuration");
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

    private PublicKey parsePublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Missing JWT public key configuration");
        }
        String normalized = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] keyBytes = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT public key is not a valid X.509 RSA key", ex);
        }
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    public record AuthenticatedUser(Integer id, String email, String role) {
    }
}
