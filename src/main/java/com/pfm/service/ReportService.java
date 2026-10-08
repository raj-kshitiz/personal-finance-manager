package com.pfm.service;

import com.pfm.dto.response.MonthlyReportResponse;
import com.pfm.dto.response.YearlyReportResponse;

/**
 * Report generation. All aggregation happens in the database and is scoped to the authenticated
 * user, so deleted transactions never appear in a report.
 */
public interface ReportService {

    /**
     * Builds the report for one calendar month.
     *
     * @param year  the year, 1970-2100
     * @param month the month, 1-12
     * @return totals per category split by type plus net savings
     * @throws com.pfm.exception.BadRequestException if {@code month} or {@code year} is out of
     *                                               range
     */
    MonthlyReportResponse monthlyReport(int year, int month);

    /**
     * Builds the report for one calendar year.
     *
     * @param year the year, 1970-2100
     * @return totals per category split by type plus net savings
     * @throws com.pfm.exception.BadRequestException if {@code year} is out of range
     */
    YearlyReportResponse yearlyReport(int year);
}
