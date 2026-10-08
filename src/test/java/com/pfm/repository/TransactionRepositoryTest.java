package com.pfm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import com.pfm.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * {@code @DataJpaTest} coverage for {@link TransactionRepository} against a real (H2) database.
 * These tests exist because {@link com.pfm.service.TransactionServiceImplTest} mocks this
 * repository entirely, so the hand-written {@code order by t.date desc, t.id desc} clause, the
 * per-user scoping, the aggregate queries and the "deleted rows vanish from aggregates" rule were
 * previously only exercised (if at all) by an end-to-end test using distinct dates, never pinning
 * the same-date id tiebreak or the deletion rule directly against the query layer.
 */
@DataJpaTest
class TransactionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionRepository transactionRepository;

    private User persistUser(String username) {
        User user = new User(username, "hash", "Test User", "+911234567890");
        return entityManager.persistAndFlush(user);
    }

    private Category persistDefaultCategory(String name, CategoryType type) {
        return entityManager.persistAndFlush(Category.defaultCategory(name, type));
    }

    private Transaction persistTransaction(User user, Category category, String amount, LocalDate date,
                                           String description) {
        Transaction transaction = new Transaction(user, category, new BigDecimal(amount), date, description);
        return entityManager.persistAndFlush(transaction);
    }

    // ---- sort order ----

    @Test
    @DisplayName("search: transactions sharing a date come back in descending id order")
    void search_sameDateTransactions_tiebrokenByIdDescending() {
        User user = persistUser("tiebreak@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        LocalDate date = LocalDate.of(2026, 1, 10);

        Transaction t1 = persistTransaction(user, salary, "100.00", date, "first");
        Transaction t2 = persistTransaction(user, salary, "200.00", date, "second");
        Transaction t3 = persistTransaction(user, salary, "300.00", date, "third");

        List<Transaction> result = transactionRepository.search(user.getId(), null, null, null, null);

        assertThat(result).extracting(Transaction::getId)
                .containsExactly(t3.getId(), t2.getId(), t1.getId());
    }

    @Test
    @DisplayName("search: overall ordering is date descending first, then id descending within a date")
    void search_mixedDates_ordersByDateDescThenIdDesc() {
        User user = persistUser("mixed-order@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        LocalDate older = LocalDate.of(2026, 1, 5);
        LocalDate newer = LocalDate.of(2026, 1, 6);

        Transaction older1 = persistTransaction(user, salary, "10.00", older, "older-1");
        Transaction older2 = persistTransaction(user, salary, "20.00", older, "older-2");
        Transaction newer1 = persistTransaction(user, salary, "30.00", newer, "newer-1");

        List<Transaction> result = transactionRepository.search(user.getId(), null, null, null, null);

        assertThat(result).extracting(Transaction::getId)
                .containsExactly(newer1.getId(), older2.getId(), older1.getId());
    }

    // ---- filtering ----

    @Test
    @DisplayName("search: all-null filters return every one of the user's transactions")
    void search_allNullFilters_returnsEverything() {
        User user = persistUser("no-filter@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        Category food = persistDefaultCategory("Food", CategoryType.EXPENSE);
        persistTransaction(user, salary, "100.00", LocalDate.of(2026, 1, 1), null);
        persistTransaction(user, food, "50.00", LocalDate.of(2026, 1, 2), null);

        List<Transaction> result = transactionRepository.search(user.getId(), null, null, null, null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("search: startDate/endDate filters the inclusive date range")
    void search_filteredByDateRange_onlyReturnsWithinBounds() {
        User user = persistUser("date-range@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        Transaction before = persistTransaction(user, salary, "1.00", LocalDate.of(2026, 1, 1), "before");
        Transaction inStart = persistTransaction(user, salary, "2.00", LocalDate.of(2026, 1, 5), "in-start");
        Transaction inEnd = persistTransaction(user, salary, "3.00", LocalDate.of(2026, 1, 10), "in-end");
        Transaction after = persistTransaction(user, salary, "4.00", LocalDate.of(2026, 1, 15), "after");

        List<Transaction> result = transactionRepository.search(user.getId(), LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 10), null, null);

        assertThat(result).extracting(Transaction::getId)
                .containsExactly(inEnd.getId(), inStart.getId());
        assertThat(result).extracting(Transaction::getId)
                .doesNotContain(before.getId(), after.getId());
    }

    @Test
    @DisplayName("search: categoryId filters to only that category's transactions")
    void search_filteredByCategoryId_onlyReturnsMatchingCategory() {
        User user = persistUser("category-id@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        Category food = persistDefaultCategory("Food", CategoryType.EXPENSE);
        Transaction salaryTx = persistTransaction(user, salary, "100.00", LocalDate.of(2026, 1, 1), null);
        persistTransaction(user, food, "50.00", LocalDate.of(2026, 1, 1), null);

        List<Transaction> result = transactionRepository.search(user.getId(), null, null, salary.getId(), null);

        assertThat(result).extracting(Transaction::getId).containsExactly(salaryTx.getId());
    }

    @Test
    @DisplayName("search: type filters to only INCOME or only EXPENSE transactions")
    void search_filteredByType_onlyReturnsMatchingType() {
        User user = persistUser("by-type@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        Category food = persistDefaultCategory("Food", CategoryType.EXPENSE);
        Transaction income = persistTransaction(user, salary, "100.00", LocalDate.of(2026, 1, 1), null);
        Transaction expense = persistTransaction(user, food, "50.00", LocalDate.of(2026, 1, 1), null);

        List<Transaction> incomeResult = transactionRepository.search(user.getId(), null, null, null,
                CategoryType.INCOME);
        List<Transaction> expenseResult = transactionRepository.search(user.getId(), null, null, null,
                CategoryType.EXPENSE);

        assertThat(incomeResult).extracting(Transaction::getId).containsExactly(income.getId());
        assertThat(expenseResult).extracting(Transaction::getId).containsExactly(expense.getId());
    }

    @Test
    @DisplayName("search: only returns rows belonging to the given user, never another user's")
    void search_scopesToOwningUserOnly() {
        User user1 = persistUser("owner-1@example.com");
        User user2 = persistUser("owner-2@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        Transaction user1Tx = persistTransaction(user1, salary, "100.00", LocalDate.of(2026, 1, 1), null);
        Transaction user2Tx = persistTransaction(user2, salary, "200.00", LocalDate.of(2026, 1, 1), null);

        List<Transaction> user1Result = transactionRepository.search(user1.getId(), null, null, null, null);
        List<Transaction> user2Result = transactionRepository.search(user2.getId(), null, null, null, null);

        assertThat(user1Result).extracting(Transaction::getId).containsExactly(user1Tx.getId());
        assertThat(user2Result).extracting(Transaction::getId).containsExactly(user2Tx.getId());
    }

    // ---- sumByType ----

    @Test
    @DisplayName("sumByType: returns one aggregated row per type present in the range")
    void sumByType_returnsOneRowPerType() {
        User user = persistUser("sum-by-type@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        Category food = persistDefaultCategory("Food", CategoryType.EXPENSE);
        persistTransaction(user, salary, "1000.00", LocalDate.of(2026, 2, 1), null);
        persistTransaction(user, salary, "500.00", LocalDate.of(2026, 2, 2), null);
        persistTransaction(user, food, "300.00", LocalDate.of(2026, 2, 3), null);

        List<TypeTotal> result = transactionRepository.sumByType(user.getId(), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28));

        assertThat(result).hasSize(2);
        TypeTotal income = result.stream().filter(r -> r.type() == CategoryType.INCOME).findFirst().orElseThrow();
        TypeTotal expense = result.stream().filter(r -> r.type() == CategoryType.EXPENSE).findFirst().orElseThrow();
        assertThat(income.total()).isEqualByComparingTo("1500.00");
        assertThat(expense.total()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("sumByType: a date range containing no transactions returns an empty list")
    void sumByType_emptyRange_returnsEmptyList() {
        User user = persistUser("sum-by-type-empty@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        persistTransaction(user, salary, "1000.00", LocalDate.of(2026, 2, 1), null);

        List<TypeTotal> result = transactionRepository.sumByType(user.getId(), LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31));

        assertThat(result).isEmpty();
    }

    // ---- sumByCategory ----

    @Test
    @DisplayName("sumByCategory: groups by category name and orders alphabetically")
    void sumByCategory_groupsByNameAndOrdersByName() {
        User user = persistUser("sum-by-category@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        Category food = persistDefaultCategory("Food", CategoryType.EXPENSE);
        Category entertainment = persistDefaultCategory("Entertainment", CategoryType.EXPENSE);
        persistTransaction(user, salary, "1000.00", LocalDate.of(2026, 2, 1), null);
        persistTransaction(user, food, "100.00", LocalDate.of(2026, 2, 2), null);
        persistTransaction(user, food, "50.00", LocalDate.of(2026, 2, 3), null);
        persistTransaction(user, entertainment, "25.00", LocalDate.of(2026, 2, 4), null);

        List<CategoryTotal> result = transactionRepository.sumByCategory(user.getId(), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28));

        assertThat(result).extracting(CategoryTotal::categoryName)
                .containsExactly("Entertainment", "Food", "Salary");
        CategoryTotal foodTotal = result.stream().filter(r -> r.categoryName().equals("Food")).findFirst()
                .orElseThrow();
        assertThat(foodTotal.total()).isEqualByComparingTo("150.00");
        assertThat(foodTotal.type()).isEqualTo(CategoryType.EXPENSE);
    }

    // ---- deletion rule ----

    @Test
    @DisplayName("a deleted transaction no longer contributes to sumByType or sumByCategory")
    void deletedTransaction_excludedFromAggregates() {
        User user = persistUser("deleted-tx@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        Transaction surviving = persistTransaction(user, salary, "100.00", LocalDate.of(2026, 4, 1), null);
        Transaction toDelete = persistTransaction(user, salary, "900.00", LocalDate.of(2026, 4, 2), null);

        transactionRepository.delete(toDelete);
        entityManager.flush();

        List<TypeTotal> typeTotals = transactionRepository.sumByType(user.getId(), LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30));
        List<CategoryTotal> categoryTotals = transactionRepository.sumByCategory(user.getId(),
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(typeTotals).hasSize(1);
        assertThat(typeTotals.get(0).total()).isEqualByComparingTo("100.00");
        assertThat(categoryTotals).hasSize(1);
        assertThat(categoryTotals.get(0).total()).isEqualByComparingTo("100.00");

        List<Transaction> searchResult = transactionRepository.search(user.getId(), null, null, null, null);
        assertThat(searchResult).extracting(Transaction::getId).containsExactly(surviving.getId());
    }

    // ---- existsByUserIdAndCategoryId ----

    @Test
    @DisplayName("existsByUserIdAndCategoryId: true when the user has a transaction in that category")
    void existsByUserIdAndCategoryId_trueWhenReferenced() {
        User user = persistUser("exists-true@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        persistTransaction(user, salary, "100.00", LocalDate.of(2026, 1, 1), null);

        boolean exists = transactionRepository.existsByUserIdAndCategoryId(user.getId(), salary.getId());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("existsByUserIdAndCategoryId: false when the user has no transaction in that category")
    void existsByUserIdAndCategoryId_falseWhenNotReferenced() {
        User user = persistUser("exists-false@example.com");
        Category salary = persistDefaultCategory("Salary", CategoryType.INCOME);
        Category food = persistDefaultCategory("Food", CategoryType.EXPENSE);
        persistTransaction(user, salary, "100.00", LocalDate.of(2026, 1, 1), null);

        boolean exists = transactionRepository.existsByUserIdAndCategoryId(user.getId(), food.getId());

        assertThat(exists).isFalse();
    }
}
