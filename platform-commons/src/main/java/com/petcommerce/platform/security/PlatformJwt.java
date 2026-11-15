package com.petcommerce.platform.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The platform's single token contract (R-SEC-01/02), shared by the gateway (reactive) and every servlet service.
 *
 * <p>Tokens are RS256 JWTs issued only by user-service with claims {@code sub} = user ID (positive integer, as a
 * string), {@code email}, {@code role} = {@code ROLE_USER}/{@code ROLE_ADMIN}, {@code iss}, {@code aud}, {@code iat},
 * {@code exp}. Verification uses the public key only.
 *
 * <p>Claim mapping: the authentication's name (principal) is the user ID; its single authority is the role.
 */
public final class PlatformJwt {

    public static final String ROLE_CLAIM = "role";
    public static final String EMAIL_CLAIM = "email";
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final Set<String> ROLES = Set.of(ROLE_USER, ROLE_ADMIN);

    private PlatformJwt() {
    }

    /** Parses a base64 X.509 RSA public key, with or without PEM armour and line breaks. */
    public static RSAPublicKey parsePublicKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing JWT public key configuration (JWT_PUBLIC_KEY / jwt.public-key)."
                    + " Run scripts/generate-dev-keys.sh to create a local key pair.");
        }
        String normalized = value.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
        try {
            byte[] bytes = Base64.getDecoder().decode(normalized);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("JWT public key is not a valid base64 X.509 RSA key", ex);
        }
    }

    /** Expiry/not-before (default clock skew), issuer, audience, numeric subject and a known role. */
    public static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>(ROLE_CLAIM, role -> role != null && ROLES.contains(role)),
                PlatformJwt::validateSubject);
    }

    /** Maps a validated token to an authentication whose name is the user ID and whose authority is the role. */
    public static JwtAuthenticationConverter authenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("sub");
        converter.setJwtGrantedAuthoritiesConverter(PlatformJwt.authoritiesConverter());
        return converter;
    }

    static Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter() {
        return jwt -> {
            String role = jwt.getClaimAsString(ROLE_CLAIM);
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority(role));
        };
    }

    private static OAuth2TokenValidatorResult validateSubject(Jwt jwt) {
        try {
            if (jwt.getSubject() != null && Long.parseLong(jwt.getSubject()) > 0) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (NumberFormatException ignored) {
            // falls through to failure
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token subject must be a user ID", null));
    }
}
