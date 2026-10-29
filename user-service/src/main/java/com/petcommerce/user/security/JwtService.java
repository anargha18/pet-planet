package com.petcommerce.user.security;

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

@Service
public class JwtService {

    @Value("${jwt.private-key:}")
    private String jwtPrivateKey;

    @Value("${jwt.public-key:}")
    private String jwtPublicKey;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Value("${jwt.issuer:pet-planet}")
    private String issuer;

    @Value("${jwt.audience:pet-planet}")
    private String audience;

    public String generateToken(String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .claims(Map.of("role", normalizeRole(role)))
                .subject(email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey(), Jwts.SIG.RS256)
                .compact();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return normalizeRole(extractAllClaims(token).get("role", String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = Jwts.parser()
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .verifyWith(getVerificationKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration() != null && claims.getExpiration().after(new Date())
                    && claims.get("role", String.class) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .requireIssuer(issuer)
                .requireAudience(audience)
                .verifyWith(getVerificationKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private PrivateKey getSigningKey() {
        return parsePrivateKey(jwtPrivateKey);
    }

    private PublicKey getVerificationKey() {
        return parsePublicKey(jwtPublicKey);
    }

    private PrivateKey parsePrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Missing JWT private key configuration");
        }
        String normalized = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
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
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
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
}
