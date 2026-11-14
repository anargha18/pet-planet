package com.petcommerce.platform.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The verified caller of an HTTP request, taken from the validated JWT. Services use it for ownership checks
 * (R-SEC-04): a user may only see records whose {@code customerId} is their own user ID; an admin may see all.
 *
 * <p>Controllers receive it as a method parameter ({@link AuthenticatedCallerArgumentResolver}); the checks throw
 * {@link AccessDeniedException}, which the shared error handler turns into a 403 {@code ApiError}.
 *
 * @param userId the user ID from the token subject (equals {@code customerId} on the service's records)
 * @param email  the email claim (may be null for tokens without it)
 * @param admin  whether the caller has {@code ROLE_ADMIN}
 */
public record AuthenticatedCaller(Long userId, String email, boolean admin) {

    public static AuthenticatedCaller from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        long userId;
        try {
            userId = Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Authenticated user ID is invalid");
        }
        boolean admin = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (PlatformJwt.ROLE_ADMIN.equals(authority.getAuthority())) {
                admin = true;
            }
        }
        String email = authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getClaimAsString(PlatformJwt.EMAIL_CLAIM)
                : null;
        return new AuthenticatedCaller(userId, email, admin);
    }

    /** True for admins, and for the owner of a record with this {@code customerId}. Unknown owner: admins only. */
    public boolean canAccess(Long ownerCustomerId) {
        return admin || (ownerCustomerId != null && ownerCustomerId.equals(userId));
    }

    /** Throws {@link AccessDeniedException} (→ 403) unless {@link #canAccess(Long)}. */
    public void requireAccessTo(Long ownerCustomerId) {
        if (!canAccess(ownerCustomerId)) {
            throw new AccessDeniedException("You are not allowed to access this resource");
        }
    }

    /** Throws {@link AccessDeniedException} (→ 403) unless the caller is an admin. */
    public void requireAdmin() {
        if (!admin) {
            throw new AccessDeniedException("Admin role required");
        }
    }
}
