package com.pfm.exception;

/**
 * Thrown when the operation conflicts with existing data (HTTP 409).
 */
public class ConflictException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message the detail message returned to the client
     */
    public ConflictException(String message) {
        super(message);
    }
}
