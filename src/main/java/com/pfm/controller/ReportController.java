package com.pfm.controller;

import com.pfm.dto.response.MonthlyReportResponse;
import com.pfm.dto.response.YearlyReportResponse;
import com.pfm.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for report generation. Thin by design: all aggregation and range validation live
 * in {@link ReportService}; errors are translated centrally by the global exception handler.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    /**
     * Creates the controller.
     *
     * @param reportService the report service
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * Builds the report for one calendar month.
     *
     * @param year  the year, 1970-2100
     * @param month the month, 1-12
     * @return 200 with per-category totals and net savings for the month
     */
    @GetMapping("/monthly/{year}/{month}")
    public ResponseEntity<MonthlyReportResponse> monthlyReport(@PathVariable int year, @PathVariable int month) {
        return ResponseEntity.ok(reportService.monthlyReport(year, month));
    }

    /**
     * Builds the report for one calendar year.
     *
     * @param year the year, 1970-2100
     * @return 200 with per-category totals and net savings for the year
     */
    @GetMapping("/yearly/{year}")
    public ResponseEntity<YearlyReportResponse> yearlyReport(@PathVariable int year) {
        return ResponseEntity.ok(reportService.yearlyReport(year));
    }
}
