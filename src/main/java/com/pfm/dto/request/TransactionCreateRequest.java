package com.pfm.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create payload for {@code POST /api/transactions}.
 *
 * @param amount      mandatory, strictly positive, at most 13 integer and 2 fraction digits
 * @param date        mandatory, {@code YYYY-MM-DD}, must not be in the future
 * @param category    mandatory category <em>name</em>, matched case-insensitively against the
 *                    default categories and the caller's own custom categories
 * @param description optional free text, at most 500 characters
 */
public record TransactionCreateRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "amount must have at most 13 integer and 2 fraction digits")
        BigDecimal amount,

        @NotNull(message = "date is required")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate date,

        @NotBlank(message = "category is required")
        @Size(max = 50, message = "category must not exceed 50 characters")
        String category,

        @Size(max = 500, message = "description must not exceed 500 characters")
        String description) {
}
