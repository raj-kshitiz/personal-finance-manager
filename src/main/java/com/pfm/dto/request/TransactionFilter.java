package com.pfm.dto.request;

import com.pfm.entity.CategoryType;
import com.pfm.exception.BadRequestException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Parsed and validated query filters for {@code GET /api/transactions}. Every component is
 * optional; {@code null} means "do not filter on this attribute".
 *
 * <p>Both {@code categoryId} and {@code category} (a category name) are supported. The assignment
 * documents {@code categoryId}; the official test script calls {@code ?category=Salary}.</p>
 *
 * @param startDate    inclusive lower date bound
 * @param endDate      inclusive upper date bound
 * @param categoryId   exact category id
 * @param categoryName category name, matched case-insensitively
 * @param type         INCOME or EXPENSE
 */
public record TransactionFilter(LocalDate startDate, LocalDate endDate, Long categoryId, String categoryName,
                                CategoryType type) {

    /**
     * Parses raw query parameters, rejecting anything malformed with a clear message.
     *
     * @param startDate  raw {@code startDate} parameter, may be {@code null} or blank
     * @param endDate    raw {@code endDate} parameter, may be {@code null} or blank
     * @param categoryId raw {@code categoryId} parameter, may be {@code null} or blank
     * @param category   raw {@code category} parameter (a name), may be {@code null} or blank
     * @param type       raw {@code type} parameter, may be {@code null} or blank
     * @return the parsed filter
     * @throws BadRequestException if a value cannot be parsed or {@code startDate > endDate}
     */
    public static TransactionFilter parse(String startDate, String endDate, String categoryId, String category,
                                          String type) {
        LocalDate from = parseDate(startDate, "startDate");
        LocalDate to = parseDate(endDate, "endDate");
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("startDate must not be after endDate");
        }
        return new TransactionFilter(from, to, parseId(categoryId), blankToNull(category), parseType(type));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate parseDate(String value, String field) {
        String raw = blankToNull(value);
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException(field + " must be a valid date in YYYY-MM-DD format");
        }
    }

    private static Long parseId(String value) {
        String raw = blankToNull(value);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new BadRequestException("categoryId must be a number");
        }
    }

    private static CategoryType parseType(String value) {
        String raw = blankToNull(value);
        if (raw == null) {
            return null;
        }
        try {
            return CategoryType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("type must be INCOME or EXPENSE");
        }
    }
}
