package com.petcommerce.platform.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.function.Consumer;

/**
 * Test-only token signer with a throwaway RSA key pair, producing tokens with the same claims as user-service.
 * Shipped in the platform-commons test-jar. Point the service under test at {@link #publicKeyBase64()}:
 *
 * <pre>{@code
 * @TestPropertySource(properties = "jwt.public-key=" + ...)   // or
 * @DynamicPropertySource static void jwt(DynamicPropertyRegistry r) { r.add("jwt.public-key", TestJwtIssuer::publicKeyBase64); }
 * }</pre>
 */
public final class TestJwtIssuer {

    private static final KeyPair KEY_PAIR = generate();

    private TestJwtIssuer() {
    }

    public static String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
    }

    public static String userToken(long userId) {
        return token(userId, PlatformJwt.ROLE_USER);
    }

    public static String adminToken(long userId) {
        return token(userId, PlatformJwt.ROLE_ADMIN);
    }

    public static String token(long userId, String role) {
        return token(claims -> claims.subject(Long.toString(userId)).claim(PlatformJwt.ROLE_CLAIM, role)
                .claim(PlatformJwt.EMAIL_CLAIM, "user" + userId + "@example.com"));
    }

    /** Valid defaults (issuer/audience {@code pet-planet}, 1 h expiry); the customizer may override anything. */
    public static String token(Consumer<JWTClaimsSet.Builder> customizer) {
        return sign(KEY_PAIR, customizer);
    }

    /** Signs with a different, untrusted key: for "wrong signature" tests. */
    public static String tokenFromUntrustedKey(long userId) {
        return sign(generate(), claims -> claims.subject(Long.toString(userId)).claim(PlatformJwt.ROLE_CLAIM, PlatformJwt.ROLE_USER));
    }

    private static String sign(KeyPair keyPair, Consumer<JWTClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer("pet-planet")
                .audience("pet-planet")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)));
        customizer.accept(claims);
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
            jwt.sign(new RSASSASigner(keyPair.getPrivate()));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
