package com.pfm.dto.response;

/**
 * Body of a successful registration.
 *
 * @param message confirmation message
 * @param userId  the new user's id
 */
public record RegisterResponse(String message, Long userId) {
}
