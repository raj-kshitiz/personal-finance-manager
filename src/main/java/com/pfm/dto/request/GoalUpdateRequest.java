package com.pfm.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Update payload for {@code PUT /api/goals/{id}}. {@code goalName} and {@code startDate} are not
 * updatable; at least one of the two fields below must be present.
 *
 * @param targetAmount optional new target amount, strictly positive when present
 * @param targetDate   optional new target date, must be in the future when present
 */
public record GoalUpdateRequest(
        @DecimalMin(value = "0.00", inclusive = false, message = "targetAmount must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "targetAmount must have at most 13 integer and 2 fraction digits")
        BigDecimal targetAmount,

        LocalDate targetDate) {
}
