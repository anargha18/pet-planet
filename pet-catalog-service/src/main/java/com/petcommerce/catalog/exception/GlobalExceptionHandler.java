package com.petcommerce.catalog.exception;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PetNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(PetNotFoundException exception, HttpServletRequest request) {
        log.warn("Pet request failed with not found: method={}, path={}",
            request.getMethod(), request.getRequestURI());
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), List.of(request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        log.warn("Request validation failed: method={}, path={}, fields={}",
            request.getMethod(), request.getRequestURI(), details.stream()
                .map(detail -> detail.substring(0, detail.indexOf(':')))
                .toList());
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneralException(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error while processing {}",
            request.getRequestURI(),
            exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", List.of(request.getRequestURI()));
    }
    
    private ResponseEntity<ApiError> response(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, details));
    }
}
