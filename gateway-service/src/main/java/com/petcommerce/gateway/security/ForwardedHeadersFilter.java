package com.petcommerce.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Cleans the headers of every routed request (runs after {@link SecurityConfig} has authenticated it):
 * <ul>
 *   <li>removes client-supplied identity/internal headers; services trust only the JWT, which is forwarded as-is;</li>
 *   <li>adds the shared {@code X-Internal-Service-Token} for user-service routes ({@code /api/v1/auth/**},
 *       {@code /api/v1/users/**}): user-service accepts gateway traffic only.</li>
 * </ul>
 * There is no per-order ownership check here any more (R-SEC-04): each service checks ownership itself.
 */
@Component
public class ForwardedHeadersFilter implements GlobalFilter, Ordered {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final List<String> UNTRUSTED_HEADERS = List.of(
            INTERNAL_TOKEN_HEADER, "X-Authenticated-User-Id", "X-Authenticated-User-Email", "X-Authenticated-User-Role");

    private final String internalServiceToken;
    private final ObjectMapper objectMapper;

    public ForwardedHeadersFilter(@Value("${gateway.user-service-internal-token:}") String internalServiceToken,
                                  ObjectMapper objectMapper) {
        this.internalServiceToken = internalServiceToken;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean userServiceRoute = path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/users") || path.startsWith("/api/v1/users/");
        if (userServiceRoute && internalServiceToken.isBlank()) {
            return SecurityConfig.write(exchange, objectMapper, HttpStatus.SERVICE_UNAVAILABLE,
                    "User service authentication is not configured");
        }
        ServerWebExchange cleaned = exchange.mutate().request(request -> request.headers(headers -> {
            UNTRUSTED_HEADERS.forEach(headers::remove);
            if (userServiceRoute) {
                headers.set(INTERNAL_TOKEN_HEADER, internalServiceToken);
            }
        })).build();
        return chain.filter(cleaned);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
