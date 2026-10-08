package com.pfm.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies the id-based {@code equals}/{@code hashCode} contract shared by every JPA entity:
 * {@link User}, {@link Category}, {@link Transaction} and {@link SavingsGoal}.
 */
class EntityIdentityTest {

    private static User newUser() {
        return new User("user@example.com", "hash", "Name", "+1234567890");
    }

    private static Category newCategory() {
        return new Category("Food", CategoryType.EXPENSE, true, null);
    }

    private static Transaction newTransaction() {
        return new Transaction(newUser(), newCategory(), BigDecimal.TEN, LocalDate.now(), "desc");
    }

    private static SavingsGoal newSavingsGoal() {
        return new SavingsGoal(newUser(), "Trip", BigDecimal.valueOf(1000), LocalDate.now().plusMonths(6),
                LocalDate.now());
    }

    // ----- User -----

    @Test
    @DisplayName("User: two instances with the same id are equal and share a hash code")
    void userSameIdEqual() {
        User a = newUser();
        User b = newUser();
        a.setId(7L);
        b.setId(7L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("User: instances with different ids are not equal")
    void userDifferentIdNotEqual() {
        User a = newUser();
        User b = newUser();
        a.setId(1L);
        b.setId(2L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("User: instances with a null id are not equal to each other")
    void userNullIdNotEqual() {
        User a = newUser();
        User b = newUser();
        assertThat(a.getId()).isNull();
        assertThat(b.getId()).isNull();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("User: not equal to null or to an unrelated type, but equal to itself")
    void userNullAndUnrelatedTypeAndSelf() {
        User a = newUser();
        a.setId(1L);
        assertThat(a).isNotEqualTo(null);
        assertThat((Object) a).isNotEqualTo("not a user");
        assertThat(a).isEqualTo(a);
    }

    // ----- Category -----

    @Test
    @DisplayName("Category: two instances with the same id are equal and share a hash code")
    void categorySameIdEqual() {
        Category a = newCategory();
        Category b = newCategory();
        a.setId(7L);
        b.setId(7L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Category: instances with different ids are not equal")
    void categoryDifferentIdNotEqual() {
        Category a = newCategory();
        Category b = newCategory();
        a.setId(1L);
        b.setId(2L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Category: instances with a null id are not equal to each other")
    void categoryNullIdNotEqual() {
        Category a = newCategory();
        Category b = newCategory();
        assertThat(a.getId()).isNull();
        assertThat(b.getId()).isNull();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Category: not equal to null or to an unrelated type, but equal to itself")
    void categoryNullAndUnrelatedTypeAndSelf() {
        Category a = newCategory();
        a.setId(1L);
        assertThat(a).isNotEqualTo(null);
        assertThat((Object) a).isNotEqualTo("not a category");
        assertThat(a).isEqualTo(a);
    }

    // ----- Transaction -----

    @Test
    @DisplayName("Transaction: two instances with the same id are equal and share a hash code")
    void transactionSameIdEqual() {
        Transaction a = newTransaction();
        Transaction b = newTransaction();
        a.setId(7L);
        b.setId(7L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("Transaction: instances with different ids are not equal")
    void transactionDifferentIdNotEqual() {
        Transaction a = newTransaction();
        Transaction b = newTransaction();
        a.setId(1L);
        b.setId(2L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Transaction: instances with a null id are not equal to each other")
    void transactionNullIdNotEqual() {
        Transaction a = newTransaction();
        Transaction b = newTransaction();
        assertThat(a.getId()).isNull();
        assertThat(b.getId()).isNull();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Transaction: not equal to null or to an unrelated type, but equal to itself")
    void transactionNullAndUnrelatedTypeAndSelf() {
        Transaction a = newTransaction();
        a.setId(1L);
        assertThat(a).isNotEqualTo(null);
        assertThat((Object) a).isNotEqualTo("not a transaction");
        assertThat(a).isEqualTo(a);
    }

    // ----- SavingsGoal -----

    @Test
    @DisplayName("SavingsGoal: two instances with the same id are equal and share a hash code")
    void savingsGoalSameIdEqual() {
        SavingsGoal a = newSavingsGoal();
        SavingsGoal b = newSavingsGoal();
        a.setId(7L);
        b.setId(7L);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("SavingsGoal: instances with different ids are not equal")
    void savingsGoalDifferentIdNotEqual() {
        SavingsGoal a = newSavingsGoal();
        SavingsGoal b = newSavingsGoal();
        a.setId(1L);
        b.setId(2L);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("SavingsGoal: instances with a null id are not equal to each other")
    void savingsGoalNullIdNotEqual() {
        SavingsGoal a = newSavingsGoal();
        SavingsGoal b = newSavingsGoal();
        assertThat(a.getId()).isNull();
        assertThat(b.getId()).isNull();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("SavingsGoal: not equal to null or to an unrelated type, but equal to itself")
    void savingsGoalNullAndUnrelatedTypeAndSelf() {
        SavingsGoal a = newSavingsGoal();
        a.setId(1L);
        assertThat(a).isNotEqualTo(null);
        assertThat((Object) a).isNotEqualTo("not a savings goal");
        assertThat(a).isEqualTo(a);
    }

    @Test
    @DisplayName("cross-type: entities of different classes sharing id 1 are never equal to each other")
    void crossTypeNeverEqual() {
        User user = newUser();
        user.setId(1L);
        Category category = newCategory();
        category.setId(1L);
        assertThat((Object) user).isNotEqualTo(category);
    }
}
