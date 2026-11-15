package com.petcommerce.order.security;

import com.petcommerce.order.domain.model.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Authorization rules for orders: who is the caller, and may they see or change a given order.
 * Business services call this; it holds no business logic itself.
 */
@Component
public class OrderAccessPolicy {

    /** Returns the authenticated caller's customer ID (the integer user ID from the token). */
    public Long requireCustomerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        return customerIdFromPrincipal(authentication.getName());
    }

    /**
     * Resolves which customer's orders a list request may see: admins may pass any (or no) customer ID;
     * users are always scoped to themselves.
     */
    public Long resolveListScope(Long requestedCustomerId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (hasRole(authentication, "ADMIN")) {
            return requestedCustomerId;
        }
        Long principalCustomerId = requireCustomerId(authentication);
        if (requestedCustomerId != null && !requestedCustomerId.equals(principalCustomerId)) {
            throw new AccessDeniedException("You can only list your own orders");
        }
        return principalCustomerId;
    }

    /** Throws {@link AccessDeniedException} unless the caller is an admin or the order's owner. */
    public void checkCanAccess(Order order, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (hasRole(authentication, "ADMIN")) {
            return;
        }
        if (!hasRole(authentication, "USER")) {
            throw new AccessDeniedException("Insufficient permissions");
        }
        Long authenticatedCustomerId = requireCustomerId(authentication);
        if (!authenticatedCustomerId.equals(order.getCustomerId())) {
            throw new AccessDeniedException("You are not allowed to access this order");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String authorityName = authority.getAuthority();
            if (("ROLE_" + role).equalsIgnoreCase(authorityName) || role.equalsIgnoreCase(authorityName)) {
                return true;
            }
        }
        return false;
    }

    private Long customerIdFromPrincipal(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            throw new AccessDeniedException("Authentication principal is missing");
        }
        try {
            return (long) Integer.parseInt(principalName);
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Authenticated user ID is invalid", ex);
        }
    }
}
