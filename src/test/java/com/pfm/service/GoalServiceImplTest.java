package com.pfm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.pfm.service.impl.GoalServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mockito unit tests for {@link GoalServiceImpl}, covering every validation rule, the
 * 404-vs-403 ownership policy and every worked progress example from the assignment and the
 * official test script.
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final LocalDate TODAY = LocalDate.of(2024, 6, 15);

    @Mock
    private SavingsGoalRepository savingsGoalRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private GoalServiceImpl service;
    private User caller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        service = new GoalServiceImpl(savingsGoalRepository, transactionRepository, currentUserProvider, clock);
        caller = new User("user@example.com", "hash", "User One", "+911234567890");
        caller.setId(USER_ID);
    }

    private SavingsGoal goal(Long id, User owner, String name, String targetAmount, LocalDate targetDate,
                              LocalDate startDate) {
        SavingsGoal goal = new SavingsGoal(owner, name, new BigDecimal(targetAmount), targetDate, startDate);
        goal.setId(id);
        return goal;
    }

    private void stubTotals(LocalDate startDate, LocalDate endDate, String income, String expense) {
        when(transactionRepository.sumByType(USER_ID, startDate, endDate)).thenReturn(List.of(
                new TypeTotal(CategoryType.INCOME, new BigDecimal(income)),
                new TypeTotal(CategoryType.EXPENSE, new BigDecimal(expense))));
    }

    // ---- createGoal ----

    @Test
    void createGoalDefaultsStartDateToToday() {
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        when(savingsGoalRepository.save(any(SavingsGoal.class))).thenAnswer(invocation -> {
            SavingsGoal g = invocation.getArgument(0);
            g.setId(10L);
            return g;
        });
        when(transactionRepository.sumByType(eq(USER_ID), eq(TODAY), eq(TODAY))).thenReturn(List.of());

        GoalCreateRequest request = new GoalCreateRequest("Emergency Fund", new BigDecimal("10000.00"),
                LocalDate.of(2027, 1, 1), null);

        GoalResponse response = service.createGoal(request);

        assertThat(response.startDate()).isEqualTo(TODAY);
        assertThat(response.currentProgress().toString()).isEqualTo("0");
        assertThat(response.progressPercentage()).isEqualTo(0.0);
    }

    @Test
    void createGoalWithExplicitStartDateMatchesWorkedExample6550() {
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        when(savingsGoalRepository.save(any(SavingsGoal.class))).thenAnswer(invocation -> {
            SavingsGoal g = invocation.getArgument(0);
            g.setId(11L);
            return g;
        });
        stubTotals(startDate, TODAY, "7000.00", "450.00");

        GoalCreateRequest request = new GoalCreateRequest("Emergency Fund", new BigDecimal("10000.00"),
                LocalDate.of(2027, 1, 1), startDate);

        GoalResponse response = service.createGoal(request);

        assertThat(response.currentProgress()).isEqualByComparingTo("6550.00");
        assertThat(response.progressPercentage()).isEqualTo(65.5);
        assertThat(response.remainingAmount()).isEqualByComparingTo("3450.00");
        assertThat(response.targetAmount()).isEqualByComparingTo("10000.00");
    }

    @Test
    void createGoalWithNoTransactionsYieldsZeroProgressAndZeroPercentage() {
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        when(savingsGoalRepository.save(any(SavingsGoal.class))).thenAnswer(invocation -> {
            SavingsGoal g = invocation.getArgument(0);
            g.setId(12L);
            return g;
        });
        when(transactionRepository.sumByType(USER_ID, startDate, TODAY)).thenReturn(List.of());

        GoalCreateRequest request = new GoalCreateRequest("Vacation Fund", new BigDecimal("5000.00"),
                LocalDate.of(2027, 12, 1), startDate);

        GoalResponse response = service.createGoal(request);

        assertThat(response.currentProgress().toString()).isEqualTo("0");
        assertThat(response.progressPercentage()).isEqualTo(0.0);
    }

    @Test
    void createGoalWithPastTargetDateThrowsBadRequest() {
        GoalCreateRequest request = new GoalCreateRequest("Invalid Goal", new BigDecimal("5000.00"),
                LocalDate.of(2023, 1, 1), null);

        assertThatThrownBy(() -> service.createGoal(request)).isInstanceOf(BadRequestException.class);
        verify(savingsGoalRepository, never()).save(any());
    }

    @Test
    void createGoalWithTargetDateEqualToTodayThrowsBadRequest() {
        GoalCreateRequest request = new GoalCreateRequest("Invalid Goal", new BigDecimal("5000.00"),
                TODAY, null);

        assertThatThrownBy(() -> service.createGoal(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createGoalWithStartDateAfterTargetDateThrowsBadRequest() {
        GoalCreateRequest request = new GoalCreateRequest("Invalid Dates Goal", new BigDecimal("5000.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

        assertThatThrownBy(() -> service.createGoal(request)).isInstanceOf(BadRequestException.class);
        verify(savingsGoalRepository, never()).save(any());
    }

    @Test
    void createGoalWithStartDateEqualToTargetDateThrowsBadRequest() {
        LocalDate date = LocalDate.of(2027, 1, 1);
        GoalCreateRequest request = new GoalCreateRequest("Invalid Dates Goal", new BigDecimal("5000.00"),
                date, date);

        assertThatThrownBy(() -> service.createGoal(request)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createGoalWithNegativeProgressClampsPercentageToZeroAndInflatesRemaining() {
        // remainingAmount = max(targetAmount - currentProgress, 0); a negative currentProgress
        // therefore pushes remaining ABOVE the target amount rather than merely restoring it.
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        when(savingsGoalRepository.save(any(SavingsGoal.class))).thenAnswer(invocation -> {
            SavingsGoal g = invocation.getArgument(0);
            g.setId(13L);
            return g;
        });
        stubTotals(startDate, TODAY, "100.00", "900.00");

        GoalCreateRequest request = new GoalCreateRequest("Overspent Goal", new BigDecimal("5000.00"),
                LocalDate.of(2027, 1, 1), startDate);

        GoalResponse response = service.createGoal(request);

        assertThat(response.currentProgress()).isEqualByComparingTo("-800.00");
        assertThat(response.progressPercentage()).isEqualTo(0.0);
        assertThat(response.remainingAmount()).isEqualByComparingTo("5800.00");
    }

    // ---- getGoal / ownership ----

    @Test
    void getGoalNotFoundThrowsResourceNotFound() {
        when(savingsGoalRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGoal(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getGoalOwnedByAnotherUserThrowsForbidden() {
        User otherOwner = new User("other@example.com", "hash", "Other User", "+911234567891");
        otherOwner.setId(OTHER_USER_ID);
        SavingsGoal foreignGoal = goal(5L, otherOwner, "Other's Goal", "1000.00",
                LocalDate.of(2027, 1, 1), LocalDate.of(2024, 1, 1));
        when(savingsGoalRepository.findById(5L)).thenReturn(Optional.of(foreignGoal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        assertThatThrownBy(() -> service.getGoal(5L)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void getGoalReturnsWorkedExampleProgress1000Target5000() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        SavingsGoal goal = goal(7L, caller, "Savings", "5000.00", LocalDate.of(2027, 1, 1), startDate);
        when(savingsGoalRepository.findById(7L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        stubTotals(startDate, TODAY, "1000.00", "0.00");

        GoalResponse response = service.getGoal(7L);

        assertThat(response.currentProgress()).isEqualByComparingTo("1000.00");
        assertThat(response.progressPercentage()).isEqualTo(20.0);
        assertThat(response.remainingAmount()).isEqualByComparingTo("4000.00");
    }

    @Test
    void getGoalReturnsWorkedExampleProgress1000Target6000SixteenSixtySeven() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        SavingsGoal goal = goal(8L, caller, "Savings", "6000.00", LocalDate.of(2027, 1, 1), startDate);
        when(savingsGoalRepository.findById(8L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        stubTotals(startDate, TODAY, "1000.00", "0.00");

        GoalResponse response = service.getGoal(8L);

        assertThat(response.progressPercentage()).isEqualTo(16.67);
        assertThat(response.remainingAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void getGoalReturnsZeroProgressSerialisedAsIntegerZero() {
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        SavingsGoal goal = goal(9L, caller, "Vacation Fund", "5000.00", LocalDate.of(2027, 12, 1), startDate);
        when(savingsGoalRepository.findById(9L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(transactionRepository.sumByType(USER_ID, startDate, TODAY)).thenReturn(List.of());

        GoalResponse response = service.getGoal(9L);

        assertThat(response.currentProgress().toString()).isEqualTo("0");
        assertThat(response.progressPercentage()).isEqualTo(0.0);
    }

    @Test
    void getGoalReturnsProgress2500Target5000PercentageFifty() {
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        SavingsGoal goal = goal(14L, caller, "Vacation Fund", "5000.00", LocalDate.of(2027, 12, 1), startDate);
        when(savingsGoalRepository.findById(14L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        stubTotals(startDate, TODAY, "3000.00", "500.00");

        GoalResponse response = service.getGoal(14L);

        assertThat(response.currentProgress()).isEqualByComparingTo("2500.00");
        assertThat(response.progressPercentage()).isEqualTo(50.0);
        assertThat(response.remainingAmount()).isEqualByComparingTo("2500.00");
    }

    @Test
    void getGoalReturnsProgress3000Target5000PercentageSixty() {
        LocalDate startDate = LocalDate.of(2024, 2, 1);
        SavingsGoal goal = goal(15L, caller, "Vacation Fund", "5000.00", LocalDate.of(2027, 12, 1), startDate);
        when(savingsGoalRepository.findById(15L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(transactionRepository.sumByType(USER_ID, startDate, TODAY)).thenReturn(List.of(
                new TypeTotal(CategoryType.INCOME, new BigDecimal("3000.00"))));

        GoalResponse response = service.getGoal(15L);

        assertThat(response.currentProgress()).isEqualByComparingTo("3000.00");
        assertThat(response.progressPercentage()).isEqualTo(60.0);
    }

    // ---- updateGoal ----

    @Test
    void updateGoalTargetAmountRecomputesWorkedExample9050Target15000() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        SavingsGoal goal = goal(20L, caller, "Emergency Fund", "10000.00", LocalDate.of(2027, 1, 1), startDate);
        when(savingsGoalRepository.findById(20L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(savingsGoalRepository.save(goal)).thenReturn(goal);
        stubTotals(startDate, TODAY, "10000.00", "950.00");

        GoalUpdateRequest request = new GoalUpdateRequest(new BigDecimal("15000.00"), null);
        GoalResponse response = service.updateGoal(20L, request);

        assertThat(response.targetAmount()).isEqualByComparingTo("15000.00");
        assertThat(response.currentProgress()).isEqualByComparingTo("9050.00");
        assertThat(response.progressPercentage()).isEqualTo(60.33);
        assertThat(response.remainingAmount()).isEqualByComparingTo("5950.00");
    }

    @Test
    void updateGoalTargetDateOnlySucceeds() {
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        SavingsGoal goal = goal(21L, caller, "Emergency Fund", "10000.00", LocalDate.of(2027, 1, 1), startDate);
        when(savingsGoalRepository.findById(21L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(savingsGoalRepository.save(goal)).thenReturn(goal);
        when(transactionRepository.sumByType(USER_ID, startDate, TODAY)).thenReturn(List.of());

        GoalUpdateRequest request = new GoalUpdateRequest(null, LocalDate.of(2028, 1, 1));
        GoalResponse response = service.updateGoal(21L, request);

        assertThat(response.targetDate()).isEqualTo(LocalDate.of(2028, 1, 1));
    }

    @Test
    void updateGoalWithEmptyBodyThrowsBadRequest() {
        SavingsGoal goal = goal(22L, caller, "Emergency Fund", "10000.00", LocalDate.of(2027, 1, 1),
                LocalDate.of(2024, 1, 1));
        when(savingsGoalRepository.findById(22L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        GoalUpdateRequest request = new GoalUpdateRequest(null, null);

        assertThatThrownBy(() -> service.updateGoal(22L, request)).isInstanceOf(BadRequestException.class);
        verify(savingsGoalRepository, never()).save(any());
    }

    @Test
    void updateGoalNotFoundThrowsResourceNotFound() {
        when(savingsGoalRepository.findById(999L)).thenReturn(Optional.empty());

        GoalUpdateRequest request = new GoalUpdateRequest(new BigDecimal("5000.00"), null);

        assertThatThrownBy(() -> service.updateGoal(999L, request)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateForeignGoalThrowsForbidden() {
        User otherOwner = new User("other@example.com", "hash", "Other User", "+911234567891");
        otherOwner.setId(OTHER_USER_ID);
        SavingsGoal foreignGoal = goal(23L, otherOwner, "Other's Goal", "1000.00",
                LocalDate.of(2027, 1, 1), LocalDate.of(2024, 1, 1));
        when(savingsGoalRepository.findById(23L)).thenReturn(Optional.of(foreignGoal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        GoalUpdateRequest request = new GoalUpdateRequest(new BigDecimal("5000.00"), null);

        assertThatThrownBy(() -> service.updateGoal(23L, request)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void updateGoalWithNonFutureTargetDateThrowsBadRequest() {
        SavingsGoal goal = goal(24L, caller, "Emergency Fund", "10000.00", LocalDate.of(2027, 1, 1),
                LocalDate.of(2024, 1, 1));
        when(savingsGoalRepository.findById(24L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        GoalUpdateRequest request = new GoalUpdateRequest(null, TODAY);

        assertThatThrownBy(() -> service.updateGoal(24L, request)).isInstanceOf(BadRequestException.class);
        verify(savingsGoalRepository, never()).save(any());
    }

    @Test
    void updateGoalWithNewTargetDateBeforeStartDateThrowsBadRequest() {
        // startDate is after the new targetDate being requested, even though that date is still in
        // the future relative to "today" - the startDate < targetDate invariant must still hold.
        SavingsGoal goal = goal(25L, caller, "Emergency Fund", "10000.00", LocalDate.of(2027, 1, 1),
                LocalDate.of(2024, 6, 20));
        when(savingsGoalRepository.findById(25L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        GoalUpdateRequest invalidRequest = new GoalUpdateRequest(null, LocalDate.of(2024, 6, 18));

        assertThatThrownBy(() -> service.updateGoal(25L, invalidRequest)).isInstanceOf(BadRequestException.class);
        verify(savingsGoalRepository, never()).save(any());
    }

    // ---- deleteGoal ----

    @Test
    void deleteGoalSucceeds() {
        SavingsGoal goal = goal(30L, caller, "Emergency Fund", "10000.00", LocalDate.of(2027, 1, 1),
                LocalDate.of(2024, 1, 1));
        when(savingsGoalRepository.findById(30L)).thenReturn(Optional.of(goal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        service.deleteGoal(30L);

        verify(savingsGoalRepository).delete(goal);
    }

    @Test
    void deleteGoalNotFoundThrowsResourceNotFound() {
        when(savingsGoalRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteGoal(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteForeignGoalThrowsForbidden() {
        User otherOwner = new User("other@example.com", "hash", "Other User", "+911234567891");
        otherOwner.setId(OTHER_USER_ID);
        SavingsGoal foreignGoal = goal(31L, otherOwner, "Other's Goal", "1000.00",
                LocalDate.of(2027, 1, 1), LocalDate.of(2024, 1, 1));
        when(savingsGoalRepository.findById(31L)).thenReturn(Optional.of(foreignGoal));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        assertThatThrownBy(() -> service.deleteGoal(31L)).isInstanceOf(ForbiddenOperationException.class);
        verify(savingsGoalRepository, never()).delete(any());
    }

    // ---- listGoals ----

    @Test
    void listGoalsReturnsAllWithProgress() {
        SavingsGoal goal1 = goal(40L, caller, "Goal 1", "5000.00", LocalDate.of(2027, 1, 1),
                LocalDate.of(2024, 1, 1));
        SavingsGoal goal2 = goal(41L, caller, "Goal 2", "2000.00", LocalDate.of(2027, 1, 1),
                LocalDate.of(2024, 1, 1));
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(savingsGoalRepository.findByUserId(USER_ID)).thenReturn(List.of(goal1, goal2));
        when(transactionRepository.sumByType(eq(USER_ID), eq(LocalDate.of(2024, 1, 1)), eq(TODAY)))
                .thenReturn(List.of());

        List<GoalResponse> responses = service.listGoals();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(40L);
        assertThat(responses.get(1).id()).isEqualTo(41L);
    }
}
