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
 * Create payload for {@code POST /api/goals}.
 *
 * @param goalName     mandatory, at most 100 characters
 * @param targetAmount mandatory, strictly positive
 * @param targetDate   mandatory, strictly after today
 * @param startDate    optional, defaults to today; may be in the past but must be before
 *                     {@code targetDate}
 */
public record GoalCreateRequest(
        @NotBlank(message = "goalName is required")
        @Size(max = 100, message = "goalName must not exceed 100 characters")
        String goalName,

        @NotNull(message = "targetAmount is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "targetAmount must be greater than 0")
        @Digits(integer = 13, fraction = 2, message = "targetAmount must have at most 13 integer and 2 fraction digits")
        BigDecimal targetAmount,

        @NotNull(message = "targetDate is required")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate targetDate,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate startDate) {
}
