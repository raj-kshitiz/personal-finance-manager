package com.pfm.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload for {@code POST /api/auth/register}.
 *
 * @param username    mandatory, valid e-mail address, stored lower-cased
 * @param password    mandatory, at least 8 characters with at least one letter and one digit
 * @param fullName    mandatory, at most 100 characters
 * @param phoneNumber mandatory, 10-15 digits with an optional leading {@code +}
 */
public record RegisterRequest(
        @NotBlank(message = "username is required")
        @Email(message = "username must be a valid email address")
        @Size(max = 255, message = "username must not exceed 255 characters")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "password must contain at least one letter and one digit")
        String password,

        @NotBlank(message = "fullName is required")
        @Size(max = 100, message = "fullName must not exceed 100 characters")
        String fullName,

        @NotBlank(message = "phoneNumber is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$",
                message = "phoneNumber must be 10 to 15 digits, optionally prefixed with +")
        String phoneNumber) {
}
