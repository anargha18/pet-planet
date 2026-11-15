package com.petcommerce.gateway.security;

import com.petcommerce.platform.security.TestJwtIssuer;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gateway edge rules end to end (R-SEC-02/04/05): real RS256 tokens, the real filter chain and routes, and a stub
 * backend that echoes the headers it receives as JSON.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {

    private static final HttpServer BACKEND = startBackend();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.public-key", TestJwtIssuer::publicKeyBase64);
        registry.add("gateway.user-service-internal-token", () -> "internal-test-token");
        String backend = "http://localhost:" + BACKEND.getAddress().getPort();
        for (String service : new String[]{"user-service", "pet-catalog", "order-service", "inventory-service",
                "payment-service", "notification-service", "shipping-service", "delivery-service", "fulfillment-service"}) {
            registry.add("gateway." + service + "-url", () -> backend);
        }
    }

    @AfterAll
    static void stopBackend() {
        BACKEND.stop(0);
    }

    @Autowired
    private WebTestClient client;

    @Test
    void requestWithoutTokenIs401WithSharedErrorBody() {
        client.get().uri("/api/v1/orders").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(CorrelationIdWebFilter.HEADER)
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("Unauthorized")
                .jsonPath("$.path").isEqualTo("/api/v1/orders")
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void forgedOrExpiredTokensAre401() {
        for (String token : new String[]{TestJwtIssuer.tokenFromUntrustedKey(7),
                TestJwtIssuer.token(c -> c.subject("7").claim("role", "ROLE_USER")
                        .expirationTime(java.util.Date.from(java.time.Instant.now().minusSeconds(600))))}) {
            client.get().uri("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Test
    void validTokenIsForwardedUnchangedAndUntrustedHeadersAreStripped() {
        String token = TestJwtIssuer.userToken(42);
        client.get().uri("/api/v1/orders/5")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Authenticated-User-Id", "999")
                .header("X-Internal-Service-Token", "attacker-value")
                .header(CorrelationIdWebFilter.HEADER, "corr-123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.path").isEqualTo("/api/v1/orders/5")
                .jsonPath("$.headers.authorization").isEqualTo("Bearer " + token)
                .jsonPath("$.headers.x-correlation-id").isEqualTo("corr-123")
                .jsonPath("$.headers.x-authenticated-user-id").doesNotExist()
                .jsonPath("$.headers.x-internal-service-token").doesNotExist();
    }

    @Test
    void ownershipIsNotDecidedAtTheGateway() {
        // Another customer's records are forwarded: the owning service rejects them (R-SEC-04). No order callout.
        client.get().uri("/api/v1/notifications/customer/43")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtIssuer.userToken(42)).exchange()
                .expectStatus().isOk();
        client.get().uri("/api/v1/payments/order/77")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtIssuer.userToken(42)).exchange()
                .expectStatus().isOk();
    }

    @Test
    void publicLoginGetsInternalTokenAndNoClientIdentity() {
        client.post().uri("/api/v1/auth/login")
                .header("X-Authenticated-User-Id", "1")
                .header("X-Internal-Service-Token", "attacker-value")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.headers.x-internal-service-token").isEqualTo("internal-test-token")
                .jsonPath("$.headers.x-authenticated-user-id").doesNotExist();
    }

    @Test
    void profileRouteGetsInternalTokenAndJwt() {
        client.get().uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtIssuer.userToken(42)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.headers.x-internal-service-token").isEqualTo("internal-test-token");
    }

    @Test
    void publicCatalogReadNeedsNoToken() {
        client.get().uri("/api/v1/pets").exchange().expectStatus().isOk();
    }

    @Test
    void adminPrefixesRejectRegularUsers() {
        String user = "Bearer " + TestJwtIssuer.userToken(42);
        client.get().uri("/api/v1/users").header(HttpHeaders.AUTHORIZATION, user).exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.status").isEqualTo(403);
        client.post().uri("/api/v1/pets").header(HttpHeaders.AUTHORIZATION, user).exchange().expectStatus().isForbidden();
        client.get().uri("/api/v1/inventory/pets/1").header(HttpHeaders.AUTHORIZATION, user).exchange().expectStatus().isForbidden();
        client.put().uri("/api/v1/deliveries/order/1/status").header(HttpHeaders.AUTHORIZATION, user).exchange().expectStatus().isForbidden();
    }

    @Test
    void adminPassesAdminPrefixes() {
        String admin = "Bearer " + TestJwtIssuer.adminToken(1);
        client.get().uri("/api/v1/users").header(HttpHeaders.AUTHORIZATION, admin).exchange().expectStatus().isOk();
        client.put().uri("/api/v1/deliveries/order/1/status").header(HttpHeaders.AUTHORIZATION, admin).exchange().expectStatus().isOk();
    }

    @Test
    void internalPathsAreNeverReachable() {
        client.get().uri("/internal/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtIssuer.adminToken(1)).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void errorResponsesCarryCorsHeadersForTheStorefront() {
        client.get().uri("/api/v1/orders")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");
    }

    /** Echoes method, path and request headers (lower-cased names) as JSON. */
    private static HttpServer startBackend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                Map<String, String> headers = new TreeMap<>();
                exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
                StringBuilder json = new StringBuilder("{\"path\":\"").append(exchange.getRequestURI().getPath())
                        .append("\",\"headers\":{");
                headers.forEach((name, value) -> json.append('"').append(name).append("\":\"")
                        .append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append("\","));
                if (!headers.isEmpty()) {
                    json.setLength(json.length() - 1);
                }
                json.append("}}");
                byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
