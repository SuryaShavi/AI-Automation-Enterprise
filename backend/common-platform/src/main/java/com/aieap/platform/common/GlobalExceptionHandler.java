package com.aieap.platform.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleConstraintViolation(
        ConstraintViolationException exception,
        HttpServletRequest request
    ) {
        List<String> details = exception.getConstraintViolations()
            .stream()
            .map(this::formatConstraintViolation)
            .toList();

        return ResponseEntity.badRequest().body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError("VALIDATION_ERROR", "Request validation failed", details)
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleUnreadableBody(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError("BAD_REQUEST", "Malformed request payload", List.of())
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        String detail = exception.getName() + ": invalid value";
        return ResponseEntity.badRequest().body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError("BAD_REQUEST", "Request parameter type mismatch", List.of(detail))
        ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleMissingPart(
        MissingServletRequestPartException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError("BAD_REQUEST", "Required multipart field is missing", List.of(exception.getRequestPartName()))
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleIllegalArgument(
        IllegalArgumentException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.badRequest().body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError("BAD_REQUEST", exception.getMessage(), List.of())
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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleAccessDenied(
        AccessDeniedException exception,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError("FORBIDDEN", "Access denied", List.of())
        ));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiEnvelope<Void>> handleDataAccess(
        DataAccessException exception,
        HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        String message = "Database is unavailable or schema is incomplete. Ensure PostgreSQL is running and migrations are applied.";

        if (exception instanceof DataIntegrityViolationException) {
            status = HttpStatus.UNAUTHORIZED;
            message = "Your session user is no longer valid for current data constraints. Please sign in again.";
        } else if (exception instanceof BadSqlGrammarException) {
            message = "Database schema is out of date. Ensure Flyway migrations are applied.";
        }

        return ResponseEntity.status(status).body(new ApiEnvelope<>(
            Instant.now(),
            ResponseFactory.traceId(request),
            null,
            new ApiError(
                "DATABASE_UNAVAILABLE",
                message,
                List.of(exception.getClass().getSimpleName())
            )
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

    private String formatConstraintViolation(ConstraintViolation<?> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }
}
