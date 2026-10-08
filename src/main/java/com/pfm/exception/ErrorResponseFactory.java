package com.pfm.exception;

import com.pfm.dto.response.ErrorResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * Builds the unified error body. Used by both the {@link GlobalExceptionHandler} and the Spring
 * Security 401/403 handlers so that every error response has exactly the same shape.
 */
public final class ErrorResponseFactory {

    private ErrorResponseFactory() {
    }

    /**
     * Builds an error body without field details.
     *
     * @param status  the HTTP status
     * @param message the human readable message
     * @param path    the request path
     * @return the error body
     */
    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return of(status, message, path, null);
    }

    /**
     * Builds an error body.
     *
     * @param status  the HTTP status
     * @param message the human readable message
     * @param path    the request path
     * @param details optional per-field validation messages, may be {@code null} or empty
     * @return the error body
     */
    public static ErrorResponse of(HttpStatus status, String message, String path, List<String> details) {
        return new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                details == null || details.isEmpty() ? null : details);
    }
}
