package com.pfm.exception;

/**
 * Thrown when the requested resource does not exist (HTTP 404).
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message the detail message returned to the client
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
