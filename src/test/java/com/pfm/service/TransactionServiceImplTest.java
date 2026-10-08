package com.pfm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfm.dto.request.TransactionCreateRequest;
import com.pfm.dto.request.TransactionFilter;
import com.pfm.dto.request.TransactionUpdateRequest;
import com.pfm.dto.response.TransactionResponse;
import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import com.pfm.entity.User;
import com.pfm.exception.BadRequestException;
import com.pfm.exception.ResourceNotFoundException;
import com.pfm.repository.CategoryRepository;
import com.pfm.repository.TransactionRepository;
import com.pfm.security.CurrentUserProvider;
import com.pfm.service.impl.TransactionServiceImpl;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link TransactionServiceImpl}, covering every business rule documented in
 * {@code TransactionService}'s JavaDoc and the official test script's transaction scenario.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
    private static final Long USER_ID = 1L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private TransactionServiceImpl service;
    private User user;
    private Category salary;
    private Category food;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(transactionRepository, categoryRepository, currentUserProvider,
                FIXED_CLOCK);
        user = new User("user@example.com", "hash", "Test User", "+911234567890");
        user.setId(USER_ID);
        salary = new Category("Salary", CategoryType.INCOME, false, null);
        salary.setId(10L);
        food = new Category("Food", CategoryType.EXPENSE, false, null);
        food.setId(20L);
    }

    private Transaction transactionWithId(Long id, User owner, Category category, BigDecimal amount, LocalDate date,
                                          String description) {
        Transaction transaction = new Transaction(owner, category, amount, date, description);
        transaction.setId(id);
        return transaction;
    }

    // ---- createTransaction ----

    @Test
    void createTransaction_success_derivesTypeAndScalesAmount() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(categoryRepository.findAccessibleByName(USER_ID, "salary")).thenReturn(Optional.of(salary));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            t.setId(100L);
            return t;
        });

        TransactionCreateRequest request = new TransactionCreateRequest(new BigDecimal("5000"),
                LocalDate.of(2024, 1, 15), "salary", "January Salary");

        TransactionResponse response = service.createTransaction(request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.amount()).isEqualByComparingTo("5000.00");
        assertThat(response.date()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(response.category()).isEqualTo("Salary");
        assertThat(response.type()).isEqualTo(CategoryType.INCOME);
        assertThat(response.description()).isEqualTo("January Salary");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getCategory()).isEqualTo(salary);
    }

    @Test
    void createTransaction_futureDate_throwsBadRequest() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);

        TransactionCreateRequest request = new TransactionCreateRequest(new BigDecimal("100"),
                LocalDate.of(2026, 10, 10), "Food", null);

        assertThatThrownBy(() -> service.createTransaction(request)).isInstanceOf(BadRequestException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_todayDate_isAllowed() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(categoryRepository.findAccessibleByName(USER_ID, "Food")).thenReturn(Optional.of(food));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionCreateRequest request = new TransactionCreateRequest(new BigDecimal("50"),
                LocalDate.of(2026, 10, 9), "Food", null);

        TransactionResponse response = service.createTransaction(request);

        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    void createTransaction_unknownCategory_throwsBadRequest() {
        when(currentUserProvider.getCurrentUser()).thenReturn(user);
        when(categoryRepository.findAccessibleByName(USER_ID, "NonExistentCategory")).thenReturn(Optional.empty());

        TransactionCreateRequest request = new TransactionCreateRequest(new BigDecimal("100"),
                LocalDate.of(2024, 1, 18), "NonExistentCategory", null);

        assertThatThrownBy(() -> service.createTransaction(request)).isInstanceOf(BadRequestException.class);
        verify(transactionRepository, never()).save(any());
    }

    // ---- listTransactions ----

    @Test
    void listTransactions_noFilters_delegatesToSearchWithNulls() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        Transaction t1 = transactionWithId(2L, user, salary, new BigDecimal("5000.00"), LocalDate.of(2024, 1, 15),
                null);
        Transaction t2 = transactionWithId(1L, user, food, new BigDecimal("400.00"), LocalDate.of(2024, 1, 10),
                null);
        when(transactionRepository.search(USER_ID, null, null, null, null)).thenReturn(List.of(t1, t2));

        TransactionFilter filter = TransactionFilter.parse(null, null, null, null, null);
        List<TransactionResponse> result = service.listTransactions(filter);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2L);
        assertThat(result.get(1).id()).isEqualTo(1L);
    }

    @Test
    void listTransactions_filteredByDateRange() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        LocalDate start = LocalDate.of(2024, 1, 15);
        LocalDate end = LocalDate.of(2024, 1, 16);
        when(transactionRepository.search(USER_ID, start, end, null, null)).thenReturn(List.of());

        TransactionFilter filter = TransactionFilter.parse("2024-01-15", "2024-01-16", null, null, null);
        service.listTransactions(filter);

        verify(transactionRepository).search(USER_ID, start, end, null, null);
    }

    @Test
    void listTransactions_filteredByCategoryId() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findAccessibleById(USER_ID, 10L)).thenReturn(Optional.of(salary));
        when(transactionRepository.search(USER_ID, null, null, 10L, null)).thenReturn(List.of());

        TransactionFilter filter = TransactionFilter.parse(null, null, "10", null, null);
        service.listTransactions(filter);

        verify(transactionRepository).search(USER_ID, null, null, 10L, null);
    }

    @Test
    void listTransactions_filteredByCategoryName() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findAccessibleByName(USER_ID, "Salary")).thenReturn(Optional.of(salary));
        when(transactionRepository.search(USER_ID, null, null, 10L, null)).thenReturn(List.of());

        TransactionFilter filter = TransactionFilter.parse(null, null, null, "Salary", null);
        service.listTransactions(filter);

        verify(transactionRepository).search(USER_ID, null, null, 10L, null);
    }

    @Test
    void listTransactions_filteredByType() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(transactionRepository.search(USER_ID, null, null, null, CategoryType.INCOME)).thenReturn(List.of());

        TransactionFilter filter = TransactionFilter.parse(null, null, null, null, "income");
        service.listTransactions(filter);

        verify(transactionRepository).search(USER_ID, null, null, null, CategoryType.INCOME);
    }

    @Test
    void listTransactions_unknownCategoryId_throwsResourceNotFound() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findAccessibleById(USER_ID, 999L)).thenReturn(Optional.empty());

        TransactionFilter filter = TransactionFilter.parse(null, null, "999", null, null);

        assertThatThrownBy(() -> service.listTransactions(filter)).isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).search(any(), any(), any(), any(), any());
    }

    @Test
    void listTransactions_unknownCategoryName_throwsBadRequest() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findAccessibleByName(USER_ID, "Bogus")).thenReturn(Optional.empty());

        TransactionFilter filter = TransactionFilter.parse(null, null, null, "Bogus", null);

        assertThatThrownBy(() -> service.listTransactions(filter)).isInstanceOf(BadRequestException.class);
        verify(transactionRepository, never()).search(any(), any(), any(), any(), any());
    }

    // ---- updateTransaction ----

    @Test
    void updateTransaction_appliesOnlyPresentFields() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        Transaction existing = transactionWithId(1L, user, salary, new BigDecimal("5000.00"),
                LocalDate.of(2024, 1, 15), "January Salary");
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionUpdateRequest request = new TransactionUpdateRequest(new BigDecimal("5500.00"), null,
                "Updated January Salary with bonus");
        TransactionResponse response = service.updateTransaction(1L, request);

        assertThat(response.amount()).isEqualByComparingTo("5500.00");
        assertThat(response.description()).isEqualTo("Updated January Salary with bonus");
        assertThat(response.category()).isEqualTo("Salary");
        assertThat(response.date()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void updateTransaction_dateInBody_leavesStoredDateUnchanged() {
        // TransactionUpdateRequest has no date component by design (see
        // TransactionServiceImpl.IGNORE_DATE_ON_UPDATE); this test documents that even when a
        // caller tries to change amount/description/category together, the stored date never moves.
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        Transaction existing = transactionWithId(3L, user, food, new BigDecimal("400.00"),
                LocalDate.of(2024, 1, 17), "Groceries");
        when(transactionRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionUpdateRequest request = new TransactionUpdateRequest(new BigDecimal("450.00"), null,
                "Trying to change date");
        TransactionResponse response = service.updateTransaction(3L, request);

        assertThat(response.date()).isEqualTo(LocalDate.of(2024, 1, 17));
        assertThat(response.amount()).isEqualByComparingTo("450.00");
        assertThat(response.description()).isEqualTo("Trying to change date");
    }

    @Test
    void updateTransaction_changingCategory_rederivesType() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        Transaction existing = transactionWithId(1L, user, salary, new BigDecimal("5000.00"),
                LocalDate.of(2024, 1, 15), null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findAccessibleByName(USER_ID, "Food")).thenReturn(Optional.of(food));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionUpdateRequest request = new TransactionUpdateRequest(null, "Food", null);
        TransactionResponse response = service.updateTransaction(1L, request);

        assertThat(response.category()).isEqualTo("Food");
        assertThat(response.type()).isEqualTo(CategoryType.EXPENSE);
    }

    @Test
    void updateTransaction_otherUsersTransaction_throwsResourceNotFound() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        User otherUser = new User("other@example.com", "hash", "Other", "+911234567891");
        otherUser.setId(2L);
        Transaction existing = transactionWithId(5L, otherUser, salary, new BigDecimal("100.00"),
                LocalDate.of(2024, 1, 1), null);
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(existing));

        TransactionUpdateRequest request = new TransactionUpdateRequest(new BigDecimal("1000.00"), null, null);

        assertThatThrownBy(() -> service.updateTransaction(5L, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void updateTransaction_missingTransaction_throwsResourceNotFound() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(transactionRepository.findById(999999L)).thenReturn(Optional.empty());

        TransactionUpdateRequest request = new TransactionUpdateRequest(new BigDecimal("1000.00"), null, null);

        assertThatThrownBy(() -> service.updateTransaction(999999L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateTransaction_unknownCategory_throwsBadRequest() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        Transaction existing = transactionWithId(1L, user, salary, new BigDecimal("5000.00"),
                LocalDate.of(2024, 1, 15), null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findAccessibleByName(USER_ID, "Bogus")).thenReturn(Optional.empty());

        TransactionUpdateRequest request = new TransactionUpdateRequest(null, "Bogus", null);

        assertThatThrownBy(() -> service.updateTransaction(1L, request)).isInstanceOf(BadRequestException.class);
        verify(transactionRepository, never()).save(any());
    }

    // ---- deleteTransaction ----

    @Test
    void deleteTransaction_success_callsRepositoryDelete() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        Transaction existing = transactionWithId(1L, user, salary, new BigDecimal("5000.00"),
                LocalDate.of(2024, 1, 15), null);
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.deleteTransaction(1L);

        verify(transactionRepository).delete(existing);
    }

    @Test
    void deleteTransaction_otherUsersTransaction_throwsResourceNotFound() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        User otherUser = new User("other@example.com", "hash", "Other", "+911234567891");
        otherUser.setId(2L);
        Transaction existing = transactionWithId(5L, otherUser, salary, new BigDecimal("100.00"),
                LocalDate.of(2024, 1, 1), null);
        when(transactionRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.deleteTransaction(5L)).isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).delete(any());
    }
}
