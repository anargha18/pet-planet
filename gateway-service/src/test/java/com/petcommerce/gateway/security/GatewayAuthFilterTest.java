package com.petcommerce.gateway.security;

import com.petcommerce.gateway.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayAuthFilterTest {

    private JwtService jwtService;
    private GatewayAuthFilter gatewayAuthFilter;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "privateKey", Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        ReflectionTestUtils.setField(jwtService, "publicKey", Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "issuer", "pet-planet");
        ReflectionTestUtils.setField(jwtService, "audience", "pet-planet");
        gatewayAuthFilter = new GatewayAuthFilter(jwtService, org.springframework.web.reactive.function.client.WebClient.builder(),
                "internal-test-token", "http://localhost:8081");
    }

    @Test
    void authenticatedProfileRequestOverwritesClientIdentityHeaders() {
        String token = jwtService.generateToken(new UserResponse(
                42, "user@example.com", "Test", "User", "USER", true, null, null
        ));
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
        String token = jwtService.generateToken(new UserResponse(
                42, "user@example.com", "Test", "User", "USER", true, null, null
        ));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users").header(
                "Authorization", "Bearer " + token
        ).build());

        gatewayAuthFilter.filter(exchange, ignored -> Mono.error(new AssertionError("request must not be forwarded"))).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void regularUserCannotCreateCatalogItems() {
        String token = jwtService.generateToken(new UserResponse(
                42, "user@example.com", "Test", "User", "USER", true, null, null
        ));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/pets").header(
                "Authorization", "Bearer " + token
        ).build());

        gatewayAuthFilter.filter(exchange, ignored -> Mono.error(new AssertionError("request must not be forwarded"))).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
    }

    @Test
    void regularUserCannotListAnotherUsersNotifications() {
        String token = jwtService.generateToken(new UserResponse(
                42, "user@example.com", "Test", "User", "USER", true, null, null
        ));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/notifications/customer/43").header(
                "Authorization", "Bearer " + token
        ).build());

        gatewayAuthFilter.filter(exchange, ignored -> Mono.error(new AssertionError("request must not be forwarded"))).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
    }
}
