package com.petcommerce.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * HTTP side of log correlation (R-EVT-08): takes the caller's {@code X-Correlation-Id} (the gateway sets one on
 * every request) or creates one, puts it in the logging MDC as {@code correlationId} for the whole request, and
 * echoes it in the response header. Error bodies ({@link ApiError}) carry the same value.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    /** Same MDC key the Kafka record interceptor uses, so one log pattern covers HTTP and Kafka work. */
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        // Only accept short, log-safe values from outside; anything else is replaced.
        String correlationId = supplied != null && SAFE_VALUE.matcher(supplied).matches()
                ? supplied
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
