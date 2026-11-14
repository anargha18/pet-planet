package com.petcommerce.user.security.token;

import com.petcommerce.platform.security.PlatformJwt;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tokens issued here must pass the exact validator every other service uses (platform-commons PlatformJwt). */
class TokenIssuerTest {

    @Test
    void issuedTokenPassesPlatformValidationWithExpectedClaims() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        TokenIssuer issuer = new TokenIssuer(
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()), 3_600_000L, "pet-planet", "pet-planet");

        String token = issuer.issue(42, "person@example.com", "ADMIN");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(
                PlatformJwt.parsePublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))).build();
        decoder.setJwtValidator(PlatformJwt.validator("pet-planet", "pet-planet"));
        Jwt jwt = decoder.decode(token);

        assertEquals("42", jwt.getSubject());
        assertEquals("person@example.com", jwt.getClaimAsString("email"));
        assertEquals("ROLE_ADMIN", jwt.getClaimAsString("role"));
        assertEquals(List.of("pet-planet"), jwt.getAudience());
        assertEquals("42", PlatformJwt.authenticationConverter().convert(jwt).getName());
    }

    @Test
    void tokenFromAnotherKeyIsRejected() throws Exception {
        KeyPair signing = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KeyPair other = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        TokenIssuer issuer = new TokenIssuer(
                Base64.getEncoder().encodeToString(signing.getPrivate().getEncoded()), 3_600_000L, "pet-planet", "pet-planet");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) other.getPublic()).build();

        assertThrows(JwtException.class, () -> decoder.decode(issuer.issue(1, "a@example.com", "USER")));
    }
}
