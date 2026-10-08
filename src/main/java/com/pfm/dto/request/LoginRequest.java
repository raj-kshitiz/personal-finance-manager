package com.pfm.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Login payload for {@code POST /api/auth/login}.
 *
 * @param username the registered e-mail address, matched case-insensitively
 * @param password the plain-text password
 */
public record LoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password) {
}
