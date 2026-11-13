package com.petcommerce.gateway.security;

import io.jsonwebtoken.JwtException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    private final String internalServiceToken;
    private final WebClient orderServiceClient;

    public GatewayAuthFilter(
            JwtService jwtService,
            WebClient.Builder webClientBuilder,
            @Value("${gateway.user-service-internal-token:}") String internalServiceToken,
            @Value("${gateway.order-service-url:http://localhost:8081}") String orderServiceUrl
    ) {
        this.jwtService = jwtService;
        this.internalServiceToken = internalServiceToken;
        this.orderServiceClient = webClientBuilder.baseUrl(orderServiceUrl).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        if (method == HttpMethod.OPTIONS || path.equals("/actuator/health") || isPublicCatalogRead(path, method)) {
            return chain.filter(exchange);
        }
        if (isPublicAuthRequest(path, method)) {
            return forwardPublicAuthRequest(exchange, chain);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        JwtService.AuthenticatedUser user;
        try {
            user = jwtService.validate(authorization.substring(7));
        } catch (JwtException | IllegalArgumentException ex) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "Invalid authentication token");
        }

        if (!isAuthorized(path, method, user.role())) {
            return reject(exchange, HttpStatus.FORBIDDEN, "Access denied");
        }
        if ("ROLE_USER".equals(user.role()) && !ownsCustomerIdRoute(path, user.id())) {
            return reject(exchange, HttpStatus.FORBIDDEN, "Access denied");
        }
        boolean userServicePath = path.equals("/api/v1/users") || path.startsWith("/api/v1/users/");
        if (userServicePath && internalServiceToken.isBlank()) {
            return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, "User service authentication is not configured");
        }

        var requestBuilder = exchange.getRequest().mutate().headers(headers -> {
            headers.remove("X-Authenticated-User-Id");
            headers.remove("X-Authenticated-User-Email");
            headers.remove("X-Authenticated-User-Role");
            headers.remove("X-Internal-Service-Token");
            headers.set("X-Authenticated-User-Id", user.id().toString());
            headers.set("X-Authenticated-User-Email", user.email());
            headers.set("X-Authenticated-User-Role", user.role());
            if (userServicePath) {
                headers.set("X-Internal-Service-Token", internalServiceToken);
            }
        });
        ServerWebExchange forwardedExchange = exchange.mutate().request(requestBuilder.build()).build();
        if ("ROLE_USER".equals(user.role()) && method == HttpMethod.GET) {
            Long orderId = relatedOrderId(path);
            if (orderId != null) {
                return forwardIfOrderOwned(forwardedExchange, chain, authorization, orderId);
            }
        }
        return chain.filter(forwardedExchange);
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isPublicAuthRequest(String path, HttpMethod method) {
        return method == HttpMethod.POST && (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register"));
    }

    /**
     * Login and registration are public, but user-service only accepts gateway traffic: strip any
     * client-supplied identity headers and attach the internal service token.
     */
    private Mono<Void> forwardPublicAuthRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (internalServiceToken.isBlank()) {
            return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, "User service authentication is not configured");
        }
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove("X-Authenticated-User-Id");
            headers.remove("X-Authenticated-User-Email");
            headers.remove("X-Authenticated-User-Role");
            headers.set("X-Internal-Service-Token", internalServiceToken);
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private boolean isPublicCatalogRead(String path, HttpMethod method) {
        return path.startsWith("/api/v1/pets/") || path.equals("/api/v1/pets")
                ? method == HttpMethod.GET
                : false;
    }

    private boolean isAuthorized(String path, HttpMethod method, String role) {
        boolean admin = "ROLE_ADMIN".equals(role);
        boolean user = "ROLE_USER".equals(role);

        if (path.equals("/api/v1/users/me") || path.startsWith("/api/v1/users/me/")) {
            return user || admin;
        }
        if (path.equals("/api/v1/users") || path.startsWith("/api/v1/users/")) {
            return admin;
        }
        if (path.startsWith("/api/v1/pets/") || path.equals("/api/v1/pets")
                || path.startsWith("/api/v1/inventory/") || path.equals("/api/v1/inventory")) {
            return admin;
        }
        if (method == HttpMethod.PUT && isDeliveryCompletionPath(path)) {
            return admin;
        }
        if (path.startsWith("/api/v1/orders/")) {
            return user || admin;
        }
        return Set.of("ROLE_USER", "ROLE_ADMIN").contains(role);
    }

    private boolean ownsCustomerIdRoute(String path, Integer userId) {
        String[] parts = path.split("/");
        if (parts.length != 6 || !"customer".equals(parts[4])) {
            return true;
        }
        try {
            return userId.equals(Integer.valueOf(parts[5]));
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private Long relatedOrderId(String path) {
        String[] parts = path.split("/");
        if (parts.length != 6 || !"order".equals(parts[4])
                || !Set.of("payments", "notifications", "shipments", "deliveries", "fulfillment").contains(parts[3])) {
            return null;
        }
        try {
            return Long.valueOf(parts[5]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isDeliveryCompletionPath(String path) {
        return path.startsWith("/api/v1/shipments/order/") && path.endsWith("/delivered")
                || path.startsWith("/api/v1/deliveries/order/") && path.endsWith("/delivered");
    }

    private Mono<Void> forwardIfOrderOwned(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String authorization,
            Long orderId
    ) {
        return orderServiceClient.get()
                .uri("/api/v1/orders/{orderId}", orderId)
                .header("Authorization", authorization)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody().then(chain.filter(exchange));
                    }
                    HttpStatus status = response.statusCode().value() == HttpStatus.FORBIDDEN.value()
                            ? HttpStatus.FORBIDDEN
                            : response.statusCode().value() == HttpStatus.NOT_FOUND.value()
                                    ? HttpStatus.NOT_FOUND
                                    : HttpStatus.BAD_GATEWAY;
                    return response.releaseBody().then(reject(exchange, status, "Order access check failed"));
                })
                .onErrorResume(WebClientRequestException.class,
                        ex -> reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Order service is unavailable"));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
