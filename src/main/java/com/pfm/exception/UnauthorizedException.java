package com.pfm.exception;

/**
 * Thrown when the caller is not authenticated or supplied bad credentials (HTTP 401).
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message the detail message returned to the client
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}
