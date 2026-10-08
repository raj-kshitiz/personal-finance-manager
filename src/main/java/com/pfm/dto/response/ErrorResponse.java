package com.pfm.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The single error body used by the global exception handler and by the security entry point and
 * access denied handler.
 *
 * @param timestamp when the error was produced
 * @param status    the HTTP status code
 * @param error     the HTTP reason phrase
 * @param message   a short, human readable description
 * @param path      the request path
 * @param details   optional per-field validation messages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details) {
}
