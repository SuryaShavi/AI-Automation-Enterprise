package com.aieap.platform.common;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<String> details = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::formatFieldError)
            .toList();

        return ResponseEntity.badRequest().body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError("VALIDATION_ERROR", "Request validation failed", details)
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleStatus(
        ResponseStatusException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return ResponseEntity.status(status).body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError(status.name(), exception.getReason(), List.of())
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnexpected(
        Exception exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                List.of(exception.getClass().getSimpleName())
            )
        ));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}