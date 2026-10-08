package com.pfm.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A savings goal including its recomputed progress.
 *
 * <p>{@code progressPercentage} is a JSON floating point number rather than a fixed-scale decimal:
 * the assignment's own examples show {@code 20.0} and {@code 16.67}, and the official test script
 * compares against {@code 65.5}, {@code 50.0} and {@code 60.33}.</p>
 *
 * @param id                 the goal id
 * @param goalName           the goal name
 * @param targetAmount       the target amount, scale 2
 * @param targetDate         the target date
 * @param startDate          the date progress is accumulated from
 * @param currentProgress    net income since {@code startDate}; exact zero is emitted as {@code 0}
 * @param progressPercentage progress as a percentage of the target, clamped to [0, 100]
 * @param remainingAmount    {@code max(targetAmount - currentProgress, 0)}, scale 2
 */
public record GoalResponse(
        Long id,
        String goalName,
        BigDecimal targetAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate targetDate,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate startDate,
        BigDecimal currentProgress,
        double progressPercentage,
        BigDecimal remainingAmount) {
}
