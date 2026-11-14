package com.petcommerce.user.security.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.petcommerce.platform.security.PlatformJwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Issues RS256 access tokens with Spring Security's {@link JwtEncoder} (Nimbus) (R-SEC-01). user-service is the only
 * service holding the private key; the gateway and every other service verify tokens with the public key
 * ({@link PlatformJwt}).
 *
 * <p>Claims: {@code sub} = user ID, {@code email}, {@code role} = ROLE_USER / ROLE_ADMIN, {@code iss}, {@code aud},
 * {@code iat}, {@code exp}.
 */
@Component
public class TokenIssuer {

    private final JwtEncoder encoder;
    private final Duration lifetime;
    private final String issuer;
    private final String audience;

    public TokenIssuer(
            @Value("${jwt.private-key:}") String privateKey,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs,
            @Value("${jwt.issuer:pet-planet}") String issuer,
            @Value("${jwt.audience:pet-planet}") String audience
    ) {
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey(privateKey))));
        this.lifetime = Duration.ofMillis(expirationMs);
        this.issuer = issuer;
        this.audience = audience;
    }

    public String issue(Integer userId, String email, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(userId.toString())
                .claim(PlatformJwt.EMAIL_CLAIM, email)
                .claim(PlatformJwt.ROLE_CLAIM, normalizeRole(role))
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Builds the signing JWK from the PKCS#8 private key; the public half is derived from its CRT parameters. */
    private static RSAKey rsaKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("Missing JWT private key configuration (JWT_PRIVATE_KEY)."
                    + " Run scripts/generate-dev-keys.sh to create a local key pair.");
        }
        // Accept bare base64 or PEM: drop any "-----BEGIN/END ...-----" armour lines and whitespace.
        String normalized = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(normalized)));
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        } catch (Exception ex) {
            throw new IllegalStateException("JWT private key is not a valid PKCS#8 RSA key", ex);
        }
    }

    private static String normalizeRole(String role) {
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }
}
