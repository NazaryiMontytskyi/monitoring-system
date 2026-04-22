package com.nmontytskyi.monitoring.server.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleServiceNotFound(ServiceNotFoundException ex) {
        log.warn("Service not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(ServiceAlreadyRegisteredException.class)
    public ResponseEntity<Map<String, Object>> handleServiceAlreadyRegistered(ServiceAlreadyRegisteredException ex) {
        log.warn("Service already registered: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(ex.getMessage()));
    }

    /**
     * Handles {@link MethodArgumentNotValidException} thrown by Spring MVC when
     * {@code @Valid @RequestBody} validation fails on a POJO request body.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        log.warn("Validation failed: {}", violations);
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "violations", violations,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    /**
     * Handles {@link ConstraintViolationException} thrown by
     * {@code MethodValidationInterceptor} when {@code @Validated} class-level
     * validation fails (e.g. for method parameters like {@code List<@Valid T>}).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> violations = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        log.warn("Constraint violation: {}", violations);
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "violations", violations,
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    private Map<String, Object> errorBody(String message) {
        return Map.of(
                "error", message,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
