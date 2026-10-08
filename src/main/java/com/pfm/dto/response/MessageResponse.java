package com.pfm.dto.response;

/**
 * Generic single-message success body, e.g. {@code {"message": "Login successful"}}.
 *
 * @param message the human readable message
 */
public record MessageResponse(String message) {
}
