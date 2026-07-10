package com.printkeep.quota.core.controller;

import com.printkeep.quota.core.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Global exception handler for REST controllers.
 * Intercepts exceptions, logs them with correlation IDs, and returns structured JSON responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    /**
     * Handles validation errors.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return structured error response
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            final MethodArgumentNotValidException ex,
            final HttpServletRequest request
    ) {
        final String correlationId = getOrCreateCorrelationId();
        final String message = "Validation failed: " + ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Invalid parameters");

        LOGGER.warn("[{}] Validation error at {}: {}", correlationId, request.getRequestURI(), message);

        final ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles HTTP method not supported exceptions.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return structured error response
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            final HttpRequestMethodNotSupportedException ex,
            final HttpServletRequest request
    ) {
        final String correlationId = getOrCreateCorrelationId();
        LOGGER.warn("[{}] HTTP method not supported at {}: {}", correlationId, request.getRequestURI(), ex.getMessage());

        final ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
    }

    /**
     * Handles media type not supported exceptions.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return structured error response
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            final HttpMediaTypeNotSupportedException ex,
            final HttpServletRequest request
    ) {
        final String correlationId = getOrCreateCorrelationId();
        LOGGER.warn("[{}] Media type not supported at {}: {}", correlationId, request.getRequestURI(), ex.getMessage());

        final ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorResponse);
    }

    /**
     * Handles resource/handler not found exceptions.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return structured error response
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            final NoResourceFoundException ex,
            final HttpServletRequest request
    ) {
        final String correlationId = getOrCreateCorrelationId();
        LOGGER.warn("[{}] Resource not found at {}: {}", correlationId, request.getRequestURI(), ex.getMessage());

        final ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles general uncaught exceptions.
     *
     * @param ex      the exception
     * @param request the servlet request
     * @return structured error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(
            final Exception ex,
            final HttpServletRequest request
    ) {
        final String correlationId = getOrCreateCorrelationId();
        LOGGER.error("[{}] Internal server error at {}", correlationId, request.getRequestURI(), ex);

        final ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected internal error occurred. Reference correlation ID: " + correlationId,
                request.getRequestURI(),
                correlationId
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    private String getOrCreateCorrelationId() {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
        return correlationId;
    }
}
