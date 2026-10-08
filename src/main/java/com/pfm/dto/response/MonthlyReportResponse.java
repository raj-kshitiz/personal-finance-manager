package com.pfm.dto.response;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Body of {@code GET /api/reports/monthly/{year}/{month}}.
 *
 * @param month         the reported month, 1-12
 * @param year          the reported year
 * @param totalIncome   income totals keyed by category name, ordered by name
 * @param totalExpenses expense totals keyed by category name, ordered by name
 * @param netSavings    income minus expenses; exact zero is emitted as {@code 0}
 */
public record MonthlyReportResponse(
        int month,
        int year,
        Map<String, BigDecimal> totalIncome,
        Map<String, BigDecimal> totalExpenses,
        BigDecimal netSavings) {
}
