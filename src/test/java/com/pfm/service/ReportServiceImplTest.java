package com.pfm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.pfm.dto.response.MonthlyReportResponse;
import com.pfm.dto.response.YearlyReportResponse;
import com.pfm.entity.CategoryType;
import com.pfm.exception.BadRequestException;
import com.pfm.repository.CategoryTotal;
import com.pfm.repository.TransactionRepository;
import com.pfm.security.CurrentUserProvider;
import com.pfm.service.impl.ReportServiceImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mockito unit tests for {@link ReportServiceImpl}, covering range validation, category
 * aggregation and the zero-value serialisation rule exercised by the official test script.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final Long USER_ID = 1L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReportServiceImpl(transactionRepository, currentUserProvider);
    }

    private void lenientUserId() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    // ---- monthlyReport ----

    @Test
    void monthlyReportAggregatesIncomeAndExpensesJanuary2024() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of(
                new CategoryTotal("Food", CategoryType.EXPENSE, new BigDecimal("450.00")),
                new CategoryTotal("Freelance", CategoryType.INCOME, new BigDecimal("1500.00")),
                new CategoryTotal("Salary", CategoryType.INCOME, new BigDecimal("5500.00"))));

        MonthlyReportResponse response = service.monthlyReport(2024, 1);

        assertThat(response.month()).isEqualTo(1);
        assertThat(response.year()).isEqualTo(2024);
        assertThat(response.totalIncome()).containsExactly(
                Map.entry("Freelance", new BigDecimal("1500.00")),
                Map.entry("Salary", new BigDecimal("5500.00")));
        assertThat(response.totalExpenses()).containsExactly(Map.entry("Food", new BigDecimal("450.00")));
        assertThat(response.netSavings()).isEqualByComparingTo("6550.00");
    }

    @Test
    void monthlyReportPreservesInsertionOrderOfRepositoryResultAsLinkedHashMap() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 3, 1);
        LocalDate to = LocalDate.of(2024, 3, 31);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of(
                new CategoryTotal("Entertainment", CategoryType.EXPENSE, new BigDecimal("100.00")),
                new CategoryTotal("Food", CategoryType.EXPENSE, new BigDecimal("200.00")),
                new CategoryTotal("Rent", CategoryType.EXPENSE, new BigDecimal("300.00"))));

        MonthlyReportResponse response = service.monthlyReport(2024, 3);

        assertThat(response.totalExpenses()).isInstanceOf(LinkedHashMap.class);
        assertThat(response.totalExpenses().keySet()).containsExactly("Entertainment", "Food", "Rent");
    }

    @Test
    void monthlyReportWithNoTransactionsReturnsEmptyMapsAndZeroNetSavings() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 12, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of());

        MonthlyReportResponse response = service.monthlyReport(2024, 12);

        assertThat(response.totalIncome()).isEmpty();
        assertThat(response.totalExpenses()).isEmpty();
        assertThat(response.netSavings().toString()).isEqualTo("0");
    }

    @Test
    void monthlyReportWithMonthZeroThrowsBadRequest() {
        assertThatThrownBy(() -> service.monthlyReport(2024, 0)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void monthlyReportWithMonthThirteenThrowsBadRequest() {
        assertThatThrownBy(() -> service.monthlyReport(2024, 13)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void monthlyReportWithYearBelowRangeThrowsBadRequest() {
        assertThatThrownBy(() -> service.monthlyReport(1969, 6)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void monthlyReportWithYearAboveRangeThrowsBadRequest() {
        assertThatThrownBy(() -> service.monthlyReport(2101, 6)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void monthlyReportAcceptsBoundaryYears() {
        lenientUserId();
        when(transactionRepository.sumByCategory(USER_ID, LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 31)))
                .thenReturn(List.of());
        when(transactionRepository.sumByCategory(USER_ID, LocalDate.of(2100, 1, 1), LocalDate.of(2100, 1, 31)))
                .thenReturn(List.of());

        assertThat(service.monthlyReport(1970, 1).netSavings().toString()).isEqualTo("0");
        assertThat(service.monthlyReport(2100, 1).netSavings().toString()).isEqualTo("0");
    }

    @Test
    void monthlyReportWithIncomeOnlyLeavesExpensesEmptyAndNetSavingsEqualsIncome() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 5, 1);
        LocalDate to = LocalDate.of(2024, 5, 31);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of(
                new CategoryTotal("Salary", CategoryType.INCOME, new BigDecimal("4000.00"))));

        MonthlyReportResponse response = service.monthlyReport(2024, 5);

        assertThat(response.totalIncome()).containsExactly(Map.entry("Salary", new BigDecimal("4000.00")));
        assertThat(response.totalExpenses()).isEmpty();
        assertThat(response.netSavings()).isEqualByComparingTo("4000.00");
    }

    @Test
    void monthlyReportWithExpensesOnlyLeavesIncomeEmptyAndNetSavingsNegative() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 6, 1);
        LocalDate to = LocalDate.of(2024, 6, 30);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of(
                new CategoryTotal("Rent", CategoryType.EXPENSE, new BigDecimal("1500.00"))));

        MonthlyReportResponse response = service.monthlyReport(2024, 6);

        assertThat(response.totalIncome()).isEmpty();
        assertThat(response.totalExpenses()).containsExactly(Map.entry("Rent", new BigDecimal("1500.00")));
        assertThat(response.netSavings()).isEqualByComparingTo("-1500.00");
    }

    @Test
    void monthlyReportNetSavingsIsNegativeWhenExpensesExceedIncome() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 7, 1);
        LocalDate to = LocalDate.of(2024, 7, 31);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of(
                new CategoryTotal("Salary", CategoryType.INCOME, new BigDecimal("500.00")),
                new CategoryTotal("Rent", CategoryType.EXPENSE, new BigDecimal("1500.00"))));

        MonthlyReportResponse response = service.monthlyReport(2024, 7);

        assertThat(response.netSavings()).isEqualByComparingTo("-1000.00");
        assertThat(response.netSavings().signum()).isEqualTo(-1);
    }

    @Test
    void monthlyReportNetSavingsForEmptyPeriodSerialisesAsPlainZero() {
        // isEqualByComparingTo("0") would not catch a regression to a scale-2 zero;
        // toPlainString() is the assertion that actually pins the bare-zero serialisation rule.
        lenientUserId();
        LocalDate from = LocalDate.of(2025, 4, 1);
        LocalDate to = LocalDate.of(2025, 4, 30);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of());

        MonthlyReportResponse response = service.monthlyReport(2025, 4);

        assertThat(response.netSavings().toPlainString()).isEqualTo("0");
    }

    @Test
    void monthlyReportPreservesRepositoryOrderForIncomeCategoriesToo() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 8, 1);
        LocalDate to = LocalDate.of(2024, 8, 31);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of(
                new CategoryTotal("Bonus", CategoryType.INCOME, new BigDecimal("100.00")),
                new CategoryTotal("Freelance", CategoryType.INCOME, new BigDecimal("200.00")),
                new CategoryTotal("Salary", CategoryType.INCOME, new BigDecimal("300.00"))));

        MonthlyReportResponse response = service.monthlyReport(2024, 8);

        assertThat(response.totalIncome()).isInstanceOf(LinkedHashMap.class);
        assertThat(response.totalIncome().keySet()).containsExactly("Bonus", "Freelance", "Salary");
    }

    @Test
    void monthlyReportDecemberUsesLastDayOfMonthThirtyOneViaYearMonth() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 12, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of(
                new CategoryTotal("Salary", CategoryType.INCOME, new BigDecimal("2000.00")),
                new CategoryTotal("Food", CategoryType.EXPENSE, new BigDecimal("300.00"))));

        MonthlyReportResponse response = service.monthlyReport(2024, 12);

        assertThat(response.month()).isEqualTo(12);
        assertThat(response.netSavings()).isEqualByComparingTo("1700.00");
    }

    // ---- yearlyReport ----

    @Test
    void yearlyReportAggregatesOverWholeYear() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of(
                new CategoryTotal("Food", CategoryType.EXPENSE, new BigDecimal("450.00")),
                new CategoryTotal("Salary", CategoryType.INCOME, new BigDecimal("7000.00"))));

        YearlyReportResponse response = service.yearlyReport(2024);

        assertThat(response.year()).isEqualTo(2024);
        assertThat(response.netSavings()).isEqualByComparingTo("6550.00");
    }

    @Test
    void yearlyReportWithNoTransactionsReturnsZeroNetSavings() {
        lenientUserId();
        when(transactionRepository.sumByCategory(USER_ID, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31)))
                .thenReturn(List.of());

        YearlyReportResponse response = service.yearlyReport(2023);

        assertThat(response.totalIncome()).isEmpty();
        assertThat(response.totalExpenses()).isEmpty();
        assertThat(response.netSavings().toString()).isEqualTo("0");
    }

    @Test
    void yearlyReportAcceptsBoundaryYears() {
        lenientUserId();
        when(transactionRepository.sumByCategory(USER_ID, LocalDate.of(1970, 1, 1), LocalDate.of(1970, 12, 31)))
                .thenReturn(List.of());
        when(transactionRepository.sumByCategory(USER_ID, LocalDate.of(2100, 1, 1), LocalDate.of(2100, 12, 31)))
                .thenReturn(List.of());

        assertThat(service.yearlyReport(1970).netSavings().toPlainString()).isEqualTo("0");
        assertThat(service.yearlyReport(2100).netSavings().toPlainString()).isEqualTo("0");
    }

    @Test
    void yearlyReportWithYearBelowRangeThrowsBadRequest() {
        assertThatThrownBy(() -> service.yearlyReport(1969)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void yearlyReportWithYearAboveRangeThrowsBadRequest() {
        assertThatThrownBy(() -> service.yearlyReport(2101)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void yearlyReportMixedIncomeAndExpenseAggregationAcrossMultipleCategories() {
        lenientUserId();
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 12, 31);
        when(transactionRepository.sumByCategory(USER_ID, from, to)).thenReturn(List.of(
                new CategoryTotal("Entertainment", CategoryType.EXPENSE, new BigDecimal("200.00")),
                new CategoryTotal("Food", CategoryType.EXPENSE, new BigDecimal("450.00")),
                new CategoryTotal("Freelance", CategoryType.INCOME, new BigDecimal("1500.00")),
                new CategoryTotal("Salary", CategoryType.INCOME, new BigDecimal("5500.00"))));

        YearlyReportResponse response = service.yearlyReport(2024);

        assertThat(response.totalIncome()).hasSize(2);
        assertThat(response.totalExpenses()).hasSize(2);
        assertThat(response.netSavings()).isEqualByComparingTo("6350.00");
    }
}
