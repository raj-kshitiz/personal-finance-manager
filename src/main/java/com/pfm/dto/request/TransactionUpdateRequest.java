package com.pfm.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Update payload for {@code PUT /api/transactions/{id}}. Every field is optional; only the fields
 * present in the request body are applied.
 *
 * <p>The {@code date} field is intentionally absent: per the assignment the date of a transaction
 * cannot be modified. A {@code date} property sent by a client is silently ignored (see
 * {@code TransactionServiceImpl#DATE_UPDATE_POLICY}).</p>
 *
 * @param amount      optional new amount, strictly positive when present
 * @param category    optional new category name
 * @param description optional new description
 */
public record TransactionUpdateRequest(
        @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "amount must have at most 13 integer and 2 fraction digits")
        BigDecimal amount,

        @Size(max = 50, message = "category must not exceed 50 characters")
        String category,

        @Size(max = 500, message = "description must not exceed 500 characters")
        String description) {
}
