package com.petcommerce.gateway.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every request an {@code X-Correlation-Id} before anything else runs (R-EVT-08, HTTP side): a short, log-safe
 * client value is kept, anything else is replaced. The ID is forwarded to the downstream service (which logs it and
 * puts it in error bodies) and echoed in the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String supplied = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = supplied != null && SAFE_VALUE.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        ServerWebExchange withId = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER, correlationId)))
                .build();
        return chain.filter(withId);
    }
}
