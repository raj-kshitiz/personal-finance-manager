package com.pfm.exception;

/**
 * Thrown when the request is syntactically valid but violates a business rule (HTTP 400).
 */
public class BadRequestException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message the detail message returned to the client
     */
    public BadRequestException(String message) {
        super(message);
    }
}
