package com.petcommerce.user.security;

import com.petcommerce.platform.security.PlatformHttpSecurity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Accepts only requests carrying the gateway's shared {@code X-Internal-Service-Token} (health checks excepted), so
 * user-service cannot be called around the gateway. Runs inside the security chain, before JWT validation.
 */
@Component
public class InternalServiceKeyFilter extends OncePerRequestFilter {

    private final String expectedToken;
    private final PlatformHttpSecurity platform;

    public InternalServiceKeyFilter(@Value("${internal.service-token:}") String expectedToken, PlatformHttpSecurity platform) {
        this.expectedToken = expectedToken;
        this.platform = platform;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals("/actuator/health") || uri.startsWith("/actuator/health/") || uri.equals("/error");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String suppliedToken = request.getHeader("X-Internal-Service-Token");
        if (expectedToken.isBlank()) {
            platform.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "Internal service authentication is not configured");
            return;
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            platform.write(request, response, HttpStatus.UNAUTHORIZED, "Internal service authentication required");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
