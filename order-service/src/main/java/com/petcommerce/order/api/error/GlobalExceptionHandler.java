package com.petcommerce.order.api.error;

import com.petcommerce.platform.web.ApiError;
import com.petcommerce.order.domain.exception.InvalidOrderStateException;
import com.petcommerce.order.domain.exception.OrderNotFoundException;
import com.petcommerce.order.domain.exception.PetCatalogUnavailableException;
import com.petcommerce.order.domain.exception.PetNotFoundException;
import com.petcommerce.order.domain.exception.PetUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * order-service domain exceptions → shared {@link ApiError} body (R-SEC-06). Validation, malformed JSON, access-denied and unexpected
 * errors are handled by platform-commons' {@code PlatformExceptionHandler} (lower precedence).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiError> orderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(PetNotFoundException.class)
    public ResponseEntity<ApiError> petNotFound(PetNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(PetUnavailableException.class)
    public ResponseEntity<ApiError> petUnavailable(PetUnavailableException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ApiError> invalidOrderState(InvalidOrderStateException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(PetCatalogUnavailableException.class)
    public ResponseEntity<ApiError> catalogUnavailable(PetCatalogUnavailableException ex, HttpServletRequest request) {
        return respond(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest request) {
        log.warn("Request failed: status={}, method={}, path={}, message={}",
                status.value(), request.getMethod(), request.getRequestURI(), message);
        return ResponseEntity.status(status).body(ApiError.of(status, message, request.getRequestURI()));
    }
}
