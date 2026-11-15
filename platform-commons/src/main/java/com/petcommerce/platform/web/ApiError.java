package com.petcommerce.platform.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * The one error body every service returns (R-SEC-06):
 * {@code { "status", "error", "message", "path", "timestamp", "correlationId" }}.
 *
 * @param status        HTTP status code
 * @param error         HTTP reason phrase (e.g. "Not Found")
 * @param message       human-readable explanation, safe to show to a user
 * @param path          request path
 * @param timestamp     when the error was produced
 * @param correlationId the request's correlation ID (see {@link CorrelationIdFilter}), or null
 */
public record ApiError(int status, String error, String message, String path, Instant timestamp, String correlationId) {

    public static ApiError of(HttpStatus status, String message, String path) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, path, Instant.now(),
                MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
