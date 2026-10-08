package com.pfm.service.impl;

import com.pfm.dto.response.MonthlyReportResponse;
import com.pfm.dto.response.YearlyReportResponse;
import com.pfm.entity.CategoryType;
import com.pfm.exception.BadRequestException;
import com.pfm.repository.CategoryTotal;
import com.pfm.repository.TransactionRepository;
import com.pfm.security.CurrentUserProvider;
import com.pfm.service.ReportService;
import com.pfm.util.MoneyUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Default {@link ReportService} implementation. Every total is produced by a single
 * {@code GROUP BY} query scoped to the authenticated caller, so a deleted transaction never
 * appears in a report.
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final int MIN_YEAR = 1970;
    private static final int MAX_YEAR = 2100;

    private final TransactionRepository transactionRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Creates the service.
     *
     * @param transactionRepository data access for the category aggregates reports are built from
     * @param currentUserProvider   resolves the authenticated caller
     */
    public ReportServiceImpl(TransactionRepository transactionRepository, CurrentUserProvider currentUserProvider) {
        this.transactionRepository = transactionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MonthlyReportResponse monthlyReport(int year, int month) {
        validateYear(year);
        if (month < 1 || month > 12) {
            throw new BadRequestException("month must be between 1 and 12");
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        Totals totals = aggregate(yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return new MonthlyReportResponse(month, year, totals.income(), totals.expenses(),
                MoneyUtil.normalize(totals.net()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public YearlyReportResponse yearlyReport(int year) {
        validateYear(year);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        Totals totals = aggregate(from, to);
        return new YearlyReportResponse(year, totals.income(), totals.expenses(),
                MoneyUtil.normalize(totals.net()));
    }

    /**
     * Validates that a year falls within the supported range.
     *
     * @param year the year to validate
     * @throws BadRequestException if out of range
     */
    private void validateYear(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new BadRequestException("year must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }
    }

    /**
     * Aggregates the caller's transactions per category over an inclusive date range.
     *
     * @param from inclusive lower date bound
     * @param to   inclusive upper date bound
     * @return ordered income/expense maps plus net savings
     */
    private Totals aggregate(LocalDate from, LocalDate to) {
        Long userId = currentUserProvider.getCurrentUserId();
        List<CategoryTotal> rows = transactionRepository.sumByCategory(userId, from, to);

        Map<String, BigDecimal> income = new LinkedHashMap<>();
        Map<String, BigDecimal> expenses = new LinkedHashMap<>();
        BigDecimal incomeSum = BigDecimal.ZERO;
        BigDecimal expenseSum = BigDecimal.ZERO;

        for (CategoryTotal row : rows) {
            BigDecimal scaled = MoneyUtil.scale(row.total());
            if (row.type() == CategoryType.INCOME) {
                income.put(row.categoryName(), scaled);
                incomeSum = incomeSum.add(row.total());
            } else {
                expenses.put(row.categoryName(), scaled);
                expenseSum = expenseSum.add(row.total());
            }
        }

        return new Totals(income, expenses, incomeSum.subtract(expenseSum));
    }

    /**
     * Intermediate holder for the three pieces that both report shapes share.
     *
     * @param income   income totals keyed by category name, ordered by name
     * @param expenses expense totals keyed by category name, ordered by name
     * @param net      income sum minus expense sum, unscaled
     */
    private record Totals(Map<String, BigDecimal> income, Map<String, BigDecimal> expenses, BigDecimal net) {
    }
}
