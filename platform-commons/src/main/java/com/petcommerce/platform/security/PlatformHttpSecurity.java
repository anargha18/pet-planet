package com.petcommerce.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcommerce.platform.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Applies the platform's stateless JWT resource-server setup to a service's {@link HttpSecurity} (R-SEC-02).
 * Each service keeps its own {@code security.SecurityConfig} with its URL rules:
 *
 * <pre>{@code
 * platform.apply(http).authorizeHttpRequests(auth -> auth
 *         .requestMatchers(PlatformHttpSecurity.PUBLIC_PATHS).permitAll()
 *         .requestMatchers("/api/v1/things/**").hasAnyRole("USER", "ADMIN")
 *         .anyRequest().denyAll());
 * }</pre>
 *
 * 401 and 403 responses use the shared {@link ApiError} body. CORS is disabled: only the gateway does CORS (R-SEC-03).
 */
public class PlatformHttpSecurity {

    /** Paths every service leaves open: health probes, the error page and the OpenAPI docs. */
    public static final String[] PUBLIC_PATHS = {
            "/actuator/health", "/actuator/health/**", "/actuator/info", "/error",
            "/swagger-ui.html", "/swagger-ui/**", "/api-docs", "/api-docs/**"
    };

    private final ObjectMapper objectMapper;

    public PlatformHttpSecurity(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public HttpSecurity apply(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(PlatformJwt.authenticationConverter())));
    }

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            write(request, response, HttpStatus.UNAUTHORIZED, "Authentication required: missing, invalid or expired token");
        };
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(request, response, HttpStatus.FORBIDDEN, "Access denied");
    }

    /** Writes an {@link ApiError} body; also used by filters that reject a request before it reaches MVC. */
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(status, message, request.getRequestURI()));
    }
}
