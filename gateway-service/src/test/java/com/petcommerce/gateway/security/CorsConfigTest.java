package com.petcommerce.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** R-TEST-02: the browser preflight for login must succeed from the storefront origin (audit finding F2). */
class CorsConfigTest {

    // Requests carry an absolute URL, as real browser requests to the gateway do.
    private static final String GATEWAY = "http://localhost:8089";

    private final CorsWebFilter corsWebFilter =
            new CorsConfig().corsWebFilter("http://localhost:5173, http://127.0.0.1:5173");

    @Test
    void loginPreflightFromStorefrontIsAllowed() {
        var exchange = preflight(GATEWAY + "/api/v1/auth/login", "http://localhost:5173");

        corsWebFilter.filter(exchange, ignored -> Mono.empty()).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("http://localhost:5173", headers.getAccessControlAllowOrigin());
        assertEquals(Boolean.TRUE, headers.getAccessControlAllowCredentials());
    }

    @Test
    void preflightFromUnknownOriginIsRejected() {
        var exchange = preflight(GATEWAY + "/api/v1/auth/login", "http://evil.example");

        corsWebFilter.filter(exchange, ignored -> Mono.empty()).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
        assertNull(exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
    }

    @Test
    void originListIsTrimmedAndSplit() {
        assertEquals(List.of("http://a:1", "http://b:2"), CorsConfig.parseOrigins(" http://a:1 ,http://b:2,"));
    }

    private static MockServerWebExchange preflight(String url, String origin) {
        return MockServerWebExchange.from(MockServerHttpRequest.options(url)
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                .build());
    }
}
