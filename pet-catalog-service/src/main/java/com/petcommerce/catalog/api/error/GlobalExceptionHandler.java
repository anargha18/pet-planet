package com.petcommerce.catalog.api.error;

import com.petcommerce.platform.web.ApiError;
import com.petcommerce.catalog.domain.exception.PetNotArchivableException;
import com.petcommerce.catalog.domain.exception.PetNotFoundException;
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
 * pet-catalog-service domain exceptions → shared {@link ApiError} body (R-SEC-06). Validation, malformed JSON, access-denied and unexpected
 * errors are handled by platform-commons' {@code PlatformExceptionHandler} (lower precedence).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PetNotFoundException.class)
    public ResponseEntity<ApiError> petNotFound(PetNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(PetNotArchivableException.class)
    public ResponseEntity<ApiError> petNotArchivable(PetNotArchivableException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest request) {
        log.warn("Request failed: status={}, method={}, path={}, message={}",
                status.value(), request.getMethod(), request.getRequestURI(), message);
        return ResponseEntity.status(status).body(ApiError.of(status, message, request.getRequestURI()));
    }
}
