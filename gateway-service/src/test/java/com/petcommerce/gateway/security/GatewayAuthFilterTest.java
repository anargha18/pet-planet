package com.petcommerce.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatewayAuthFilterTest {

    private TestTokens tokens;
    private JwtService jwtService;
    private GatewayAuthFilter gatewayAuthFilter;

    @BeforeEach
    void setUp() throws Exception {
        tokens = new TestTokens();
        jwtService = tokens.jwtService();
        gatewayAuthFilter = new GatewayAuthFilter(jwtService, org.springframework.web.reactive.function.client.WebClient.builder(),
                "internal-test-token", "http://localhost:8081");
    }

    @Test
    void authenticatedProfileRequestOverwritesClientIdentityHeaders() {
        String token = tokens.token(42, "user@example.com", "USER");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")
                .header("Authorization", "Bearer " + token)
                .header("X-Authenticated-User-Id", "999")
                .header("X-Internal-Service-Token", "attacker-value")
                .build());
        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> internalToken = new AtomicReference<>();

        gatewayAuthFilter.filter(exchange, forwarded -> {
            userId.set(forwarded.getRequest().getHeaders().getFirst("X-Authenticated-User-Id"));
            internalToken.set(forwarded.getRequest().getHeaders().getFirst("X-Internal-Service-Token"));
            return Mono.empty();
        }).block();

        assertEquals("42", userId.get());
        assertEquals("internal-test-token", internalToken.get());
    }

    @Test
    void regularUserCannotAccessAdminUserManagement() {
        String token = tokens.token(42, "user@example.com", "USER");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users").header(
                "Authorization", "Bearer " + token
        ).build());

        gatewayAuthFilter.filter(exchange, ignored -> Mono.error(new AssertionError("request must not be forwarded"))).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void regularUserCannotCreateCatalogItems() {
        String token = tokens.token(42, "user@example.com", "USER");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/pets").header(
                "Authorization", "Bearer " + token
        ).build());

        gatewayAuthFilter.filter(exchange, ignored -> Mono.error(new AssertionError("request must not be forwarded"))).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void regularUserCannotListAnotherUsersNotifications() {
        String token = tokens.token(42, "user@example.com", "USER");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/notifications/customer/43").header(
                "Authorization", "Bearer " + token
        ).build());

        gatewayAuthFilter.filter(exchange, ignored -> Mono.error(new AssertionError("request must not be forwarded"))).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void publicLoginIsForwardedWithInternalTokenAndWithoutClientIdentity() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
                .header("X-Authenticated-User-Id", "1")
                .header("X-Internal-Service-Token", "attacker-value")
                .build());
        AtomicReference<String> userId = new AtomicReference<>("not-called");
        AtomicReference<String> internalToken = new AtomicReference<>();

        gatewayAuthFilter.filter(exchange, forwarded -> {
            userId.set(forwarded.getRequest().getHeaders().getFirst("X-Authenticated-User-Id"));
            internalToken.set(forwarded.getRequest().getHeaders().getFirst("X-Internal-Service-Token"));
            return Mono.empty();
        }).block();

        assertNull(userId.get());
        assertEquals("internal-test-token", internalToken.get());
    }

    @Test
    void requestWithoutTokenIsRejected() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/orders").build());

        gatewayAuthFilter.filter(exchange, ignored -> Mono.error(new AssertionError("request must not be forwarded"))).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void publicCatalogReadNeedsNoToken() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/pets").build());
        AtomicReference<Boolean> forwarded = new AtomicReference<>(false);

        gatewayAuthFilter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();

        assertEquals(true, forwarded.get());
    }
}
