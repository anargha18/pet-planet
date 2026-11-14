package com.petcommerce.user.api.error;

import com.petcommerce.platform.web.ApiError;
import com.petcommerce.user.domain.exception.LastAdminException;
import com.petcommerce.user.domain.exception.UserAlreadyExistsException;
import com.petcommerce.user.domain.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * user-service domain exceptions → shared {@link ApiError} body. Validation, access-denied and unexpected errors are
 * handled by platform-commons' {@code PlatformExceptionHandler} (lower precedence).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(UserNotFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({UserAlreadyExistsException.class, LastAdminException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, request.getRequestURI()));
    }
}
