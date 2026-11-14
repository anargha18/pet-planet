package com.petcommerce.fulfillment.api.error;

import com.petcommerce.platform.web.ApiError;
import com.petcommerce.fulfillment.domain.exception.FulfillmentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Domain exceptions → shared {@link ApiError} body (R-SEC-06). Access-denied (403), bad input (400) and unexpected
 * errors are handled by platform-commons' {@code PlatformExceptionHandler} (lower precedence).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(FulfillmentNotFoundException.class)
    public ResponseEntity<ApiError> notFound(FulfillmentNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI()));
    }
}
