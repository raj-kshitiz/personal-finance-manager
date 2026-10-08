package com.pfm.exception;

/**
 * Thrown when the caller is not allowed to perform the operation (HTTP 403).
 */
public class ForbiddenOperationException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message the detail message returned to the client
     */
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
