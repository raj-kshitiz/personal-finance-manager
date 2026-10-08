package com.pfm.service;

import com.pfm.dto.request.GoalCreateRequest;
import com.pfm.dto.request.GoalUpdateRequest;
import com.pfm.dto.response.GoalResponse;
import java.util.List;

/**
 * Savings goal management and progress calculation.
 *
 * <p>Progress for a goal is {@code sum(INCOME) - sum(EXPENSE)} over the caller's transactions whose
 * date falls in {@code [startDate, today]}, computed with database aggregates so that deleted
 * transactions are excluded automatically. {@code progressPercentage} is
 * {@code currentProgress / targetAmount * 100} rounded HALF_UP to two decimals and clamped to
 * {@code [0, 100]}; {@code remainingAmount} is {@code max(targetAmount - currentProgress, 0)}.</p>
 *
 * <p>Ownership policy for {@code {id}} endpoints: a goal that does not exist yields 404, a goal
 * that exists but belongs to another user yields 403.</p>
 */
public interface GoalService {

    /**
     * Creates a savings goal.
     *
     * @param request the validated payload; {@code startDate} defaults to today
     * @return the created goal including its progress
     * @throws com.pfm.exception.BadRequestException if {@code targetDate} is not strictly after
     *                                               today, or {@code startDate} is not strictly
     *                                               before {@code targetDate}
     */
    GoalResponse createGoal(GoalCreateRequest request);

    /**
     * Lists the caller's goals with current progress.
     *
     * @return the caller's goals, oldest first
     */
    List<GoalResponse> listGoals();

    /**
     * Reads one goal.
     *
     * @param id the goal id
     * @return the goal including its progress
     * @throws com.pfm.exception.ResourceNotFoundException   if no goal has that id
     * @throws com.pfm.exception.ForbiddenOperationException if the goal belongs to another user
     */
    GoalResponse getGoal(Long id);

    /**
     * Updates {@code targetAmount} and/or {@code targetDate}. {@code goalName} and
     * {@code startDate} are immutable.
     *
     * @param id      the goal id
     * @param request the validated payload
     * @return the goal with recalculated progress
     * @throws com.pfm.exception.BadRequestException         if no updatable field was supplied, or
     *                                                       a supplied value breaks the date rules
     * @throws com.pfm.exception.ResourceNotFoundException   if no goal has that id
     * @throws com.pfm.exception.ForbiddenOperationException if the goal belongs to another user
     */
    GoalResponse updateGoal(Long id, GoalUpdateRequest request);

    /**
     * Deletes a goal.
     *
     * @param id the goal id
     * @throws com.pfm.exception.ResourceNotFoundException   if no goal has that id
     * @throws com.pfm.exception.ForbiddenOperationException if the goal belongs to another user
     */
    void deleteGoal(Long id);
}
