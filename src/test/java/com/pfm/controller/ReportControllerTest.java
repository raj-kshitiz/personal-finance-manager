package com.pfm.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pfm.dto.response.MonthlyReportResponse;
import com.pfm.dto.response.YearlyReportResponse;
import com.pfm.exception.BadRequestException;
import com.pfm.service.ReportService;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice tests for {@link ReportController}. The service layer is mocked so
 * only HTTP wiring (status codes, JSON shape, request validation) is exercised.
 */
@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    @Test
    void monthlyReportReturns200WithExpectedShape() throws Exception {
        Map<String, BigDecimal> income = new LinkedHashMap<>();
        income.put("Freelance", new BigDecimal("1500.00"));
        income.put("Salary", new BigDecimal("5500.00"));
        Map<String, BigDecimal> expenses = new LinkedHashMap<>();
        expenses.put("Food", new BigDecimal("450.00"));
        when(reportService.monthlyReport(2024, 1)).thenReturn(
                new MonthlyReportResponse(1, 2024, income, expenses, new BigDecimal("6550.00")));

        mockMvc.perform(get("/api/reports/monthly/2024/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(1))
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.totalIncome.Salary").value(5500.00))
                .andExpect(jsonPath("$.totalIncome.Freelance").value(1500.00))
                .andExpect(jsonPath("$.totalExpenses.Food").value(450.00))
                .andExpect(jsonPath("$.netSavings").value(6550.00));
    }

    @Test
    void monthlyReportWithEmptyPeriodReturnsZeroNetSavings() throws Exception {
        when(reportService.monthlyReport(2024, 12)).thenReturn(
                new MonthlyReportResponse(12, 2024, Map.of(), Map.of(), BigDecimal.ZERO));

        mockMvc.perform(get("/api/reports/monthly/2024/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netSavings").value(0))
                .andExpect(jsonPath("$.totalIncome").isEmpty())
                .andExpect(jsonPath("$.totalExpenses").isEmpty());
    }

    @Test
    void monthlyReportWithMonthZeroReturns400() throws Exception {
        when(reportService.monthlyReport(2024, 0)).thenThrow(new BadRequestException("month must be 1-12"));

        mockMvc.perform(get("/api/reports/monthly/2024/0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void monthlyReportWithMonthThirteenReturns400() throws Exception {
        when(reportService.monthlyReport(2024, 13)).thenThrow(new BadRequestException("month must be 1-12"));

        mockMvc.perform(get("/api/reports/monthly/2024/13"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void monthlyReportWithYearBelowRangeReturns400() throws Exception {
        when(reportService.monthlyReport(1969, 1)).thenThrow(new BadRequestException("year out of range"));

        mockMvc.perform(get("/api/reports/monthly/1969/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void monthlyReportWithYearAboveRangeReturns400() throws Exception {
        when(reportService.monthlyReport(2101, 1)).thenThrow(new BadRequestException("year out of range"));

        mockMvc.perform(get("/api/reports/monthly/2101/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void monthlyReportWithNonNumericYearReturns400() throws Exception {
        mockMvc.perform(get("/api/reports/monthly/abcd/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void monthlyReportWithNonNumericMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/reports/monthly/2024/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void yearlyReportReturns200WithExpectedShape() throws Exception {
        Map<String, BigDecimal> income = Map.of("Salary", new BigDecimal("7000.00"));
        Map<String, BigDecimal> expenses = Map.of("Food", new BigDecimal("450.00"));
        when(reportService.yearlyReport(2024)).thenReturn(
                new YearlyReportResponse(2024, income, expenses, new BigDecimal("6550.00")));

        mockMvc.perform(get("/api/reports/yearly/2024"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2024))
                .andExpect(jsonPath("$.netSavings").value(6550.00));
    }

    @Test
    void yearlyReportWithEmptyPeriodReturnsZeroNetSavings() throws Exception {
        when(reportService.yearlyReport(2023)).thenReturn(
                new YearlyReportResponse(2023, Map.of(), Map.of(), BigDecimal.ZERO));

        mockMvc.perform(get("/api/reports/yearly/2023"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netSavings").value(0));
    }

    @Test
    void yearlyReportWithYearBelowRangeReturns400() throws Exception {
        when(reportService.yearlyReport(1969)).thenThrow(new BadRequestException("year out of range"));

        mockMvc.perform(get("/api/reports/yearly/1969"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void yearlyReportWithYearAboveRangeReturns400() throws Exception {
        when(reportService.yearlyReport(2101)).thenThrow(new BadRequestException("year out of range"));

        mockMvc.perform(get("/api/reports/yearly/2101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void yearlyReportWithNonNumericYearReturns400() throws Exception {
        mockMvc.perform(get("/api/reports/yearly/abcd"))
                .andExpect(status().isBadRequest());
    }
}
