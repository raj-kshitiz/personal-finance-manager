package com.pfm.service.impl;

import com.pfm.dto.request.GoalCreateRequest;
import com.pfm.dto.request.GoalUpdateRequest;
import com.pfm.dto.response.GoalResponse;
import com.pfm.entity.CategoryType;
import com.pfm.entity.SavingsGoal;
import com.pfm.entity.User;
import com.pfm.exception.BadRequestException;
import com.pfm.exception.ForbiddenOperationException;
import com.pfm.exception.ResourceNotFoundException;
import com.pfm.repository.SavingsGoalRepository;
import com.pfm.repository.TransactionRepository;
import com.pfm.repository.TypeTotal;
import com.pfm.security.CurrentUserProvider;
import com.pfm.service.GoalService;
import com.pfm.util.MoneyUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Default {@link GoalService} implementation. Progress is never stored: every read recomputes
 * {@code sum(INCOME) - sum(EXPENSE)} for the owner's transactions in {@code [startDate, today]}
 * with a single database aggregate per goal, so a deleted transaction disappears from progress
 * automatically.
 */
@Service
public class GoalServiceImpl implements GoalService {

    /** Extra precision used for the intermediate percentage division before final rounding. */
    private static final int DIVISION_SCALE = 10;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final SavingsGoalRepository savingsGoalRepository;
    private final TransactionRepository transactionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param savingsGoalRepository data access for goals
     * @param transactionRepository data access for the aggregates used to compute progress
     * @param currentUserProvider   resolves the authenticated caller
     * @param applicationClock      supplies "today" so that tests are deterministic
     */
    public GoalServiceImpl(SavingsGoalRepository savingsGoalRepository,
                           TransactionRepository transactionRepository,
                           CurrentUserProvider currentUserProvider,
                           Clock applicationClock) {
        this.savingsGoalRepository = savingsGoalRepository;
        this.transactionRepository = transactionRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = applicationClock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GoalResponse createGoal(GoalCreateRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = request.startDate() != null ? request.startDate() : today;
        LocalDate targetDate = request.targetDate();

        if (!targetDate.isAfter(today)) {
            throw new BadRequestException("targetDate must be strictly after today");
        }
        if (!startDate.isBefore(targetDate)) {
            throw new BadRequestException("startDate must be strictly before targetDate");
        }

        User user = currentUserProvider.getCurrentUser();
        SavingsGoal goal = new SavingsGoal(user, request.goalName(), request.targetAmount(), targetDate, startDate);
        SavingsGoal saved = savingsGoalRepository.save(goal);
        return toResponse(saved, today);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<GoalResponse> listGoals() {
        Long userId = currentUserProvider.getCurrentUserId();
        LocalDate today = LocalDate.now(clock);
        return savingsGoalRepository.findByUserId(userId).stream()
                .map(goal -> toResponse(goal, today))
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GoalResponse getGoal(Long id) {
        SavingsGoal goal = findOwned(id);
        return toResponse(goal, LocalDate.now(clock));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GoalResponse updateGoal(Long id, GoalUpdateRequest request) {
        SavingsGoal goal = findOwned(id);
        LocalDate today = LocalDate.now(clock);

        if (request.targetAmount() == null && request.targetDate() == null) {
            throw new BadRequestException("At least one of targetAmount or targetDate must be supplied");
        }

        LocalDate effectiveTargetDate = request.targetDate() != null ? request.targetDate() : goal.getTargetDate();
        if (request.targetDate() != null && !effectiveTargetDate.isAfter(today)) {
            throw new BadRequestException("targetDate must be strictly after today");
        }
        if (!goal.getStartDate().isBefore(effectiveTargetDate)) {
            throw new BadRequestException("startDate must be strictly before targetDate");
        }

        if (request.targetAmount() != null) {
            goal.setTargetAmount(request.targetAmount());
        }
        if (request.targetDate() != null) {
            goal.setTargetDate(request.targetDate());
        }

        SavingsGoal saved = savingsGoalRepository.save(goal);
        return toResponse(saved, today);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteGoal(Long id) {
        SavingsGoal goal = findOwned(id);
        savingsGoalRepository.delete(goal);
    }

    /**
     * Loads a goal and enforces the 404-vs-403 ownership policy.
     *
     * @param id the goal id
     * @return the goal, owned by the caller
     * @throws ResourceNotFoundException   if no goal has that id
     * @throws ForbiddenOperationException if the goal belongs to another user
     */
    private SavingsGoal findOwned(Long id) {
        SavingsGoal goal = savingsGoalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal " + id + " not found"));
        Long userId = currentUserProvider.getCurrentUserId();
        if (!goal.getUser().getId().equals(userId)) {
            throw new ForbiddenOperationException("Goal " + id + " belongs to another user");
        }
        return goal;
    }

    /**
     * Computes progress and builds the response DTO for one goal.
     *
     * @param goal  the goal
     * @param today "today" per the application clock, used as the upper progress bound
     * @return the goal response including recomputed progress
     */
    private GoalResponse toResponse(SavingsGoal goal, LocalDate today) {
        List<TypeTotal> totals = transactionRepository.sumByType(
                goal.getUser().getId(), goal.getStartDate(), today);

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (TypeTotal total : totals) {
            if (total.type() == CategoryType.INCOME) {
                income = total.total();
            } else if (total.type() == CategoryType.EXPENSE) {
                expense = total.total();
            }
        }
        BigDecimal currentProgress = income.subtract(expense);
        BigDecimal targetAmount = goal.getTargetAmount();

        double progressPercentage = computePercentage(currentProgress, targetAmount);

        BigDecimal remaining = targetAmount.subtract(currentProgress);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        return new GoalResponse(
                goal.getId(),
                goal.getGoalName(),
                MoneyUtil.scale(targetAmount),
                goal.getTargetDate(),
                goal.getStartDate(),
                MoneyUtil.normalize(currentProgress),
                progressPercentage,
                MoneyUtil.scale(remaining));
    }

    /**
     * Computes {@code currentProgress / targetAmount * 100}, rounded HALF_UP to two decimals and
     * clamped to {@code [0, 100]}.
     *
     * @param currentProgress net income since the goal's start date, may be negative
     * @param targetAmount    the goal's target amount
     * @return the clamped percentage as a {@code double}
     */
    private double computePercentage(BigDecimal currentProgress, BigDecimal targetAmount) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        BigDecimal percentage = currentProgress
                .divide(targetAmount, DIVISION_SCALE, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(MoneyUtil.SCALE, RoundingMode.HALF_UP);

        if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            percentage = BigDecimal.ZERO;
        } else if (percentage.compareTo(ONE_HUNDRED) > 0) {
            percentage = ONE_HUNDRED;
        }
        return percentage.doubleValue();
    }
}
