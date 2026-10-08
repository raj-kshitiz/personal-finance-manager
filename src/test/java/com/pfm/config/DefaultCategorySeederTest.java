package com.pfm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.repository.CategoryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Unit tests for {@link DefaultCategorySeeder}, focusing on its idempotency: only the default
 * categories that are missing from the repository are inserted.
 */
class DefaultCategorySeederTest {

    private static final Map<String, CategoryType> EXPECTED = Map.of(
            "Salary", CategoryType.INCOME,
            "Food", CategoryType.EXPENSE,
            "Rent", CategoryType.EXPENSE,
            "Transportation", CategoryType.EXPENSE,
            "Entertainment", CategoryType.EXPENSE,
            "Healthcare", CategoryType.EXPENSE,
            "Utilities", CategoryType.EXPENSE);

    private final DefaultCategorySeeder seeder = new DefaultCategorySeeder();

    @Test
    @DisplayName("when no defaults exist, all seven default categories are saved with the right name/type/custom/owner")
    void seedsAllWhenNoneExist() throws Exception {
        CategoryRepository repository = mock(CategoryRepository.class);
        when(repository.findDefaultByName(any(String.class))).thenReturn(Optional.empty());
        when(repository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationRunner runner = seeder.seedDefaultCategories(repository);
        runner.run(null);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(repository, org.mockito.Mockito.times(7)).save(captor.capture());

        List<Category> saved = captor.getAllValues();
        assertThat(saved).hasSize(7);
        assertThat(saved).extracting(Category::getName)
                .containsExactlyInAnyOrderElementsOf(EXPECTED.keySet());

        for (Category category : saved) {
            assertThat(category.getType()).isEqualTo(EXPECTED.get(category.getName()));
            assertThat(category.isCustom()).isFalse();
            assertThat(category.getOwner()).isNull();
        }

        Category salary = saved.stream().filter(c -> c.getName().equals("Salary")).findFirst().orElseThrow();
        assertThat(salary.getType()).isEqualTo(CategoryType.INCOME);
        for (String expenseName : Set.of("Food", "Rent", "Transportation", "Entertainment", "Healthcare",
                "Utilities")) {
            Category expense = saved.stream().filter(c -> c.getName().equals(expenseName)).findFirst().orElseThrow();
            assertThat(expense.getType()).isEqualTo(CategoryType.EXPENSE);
        }
    }

    @Test
    @DisplayName("when all defaults already exist, save is never called")
    void skipsAllWhenAllExist() throws Exception {
        CategoryRepository repository = mock(CategoryRepository.class);
        when(repository.findDefaultByName(any(String.class)))
                .thenAnswer(invocation -> Optional.of(Category.defaultCategory(invocation.getArgument(0),
                        CategoryType.EXPENSE)));

        ApplicationRunner runner = seeder.seedDefaultCategories(repository);
        runner.run(null);

        verify(repository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("when only some defaults exist, only the missing ones are saved")
    void seedsOnlyMissingOnes() throws Exception {
        CategoryRepository repository = mock(CategoryRepository.class);
        when(repository.findDefaultByName(eq("Salary")))
                .thenReturn(Optional.of(Category.defaultCategory("Salary", CategoryType.INCOME)));
        when(repository.findDefaultByName(eq("Food")))
                .thenReturn(Optional.of(Category.defaultCategory("Food", CategoryType.EXPENSE)));
        when(repository.findDefaultByName(eq("Rent"))).thenReturn(Optional.empty());
        when(repository.findDefaultByName(eq("Transportation"))).thenReturn(Optional.empty());
        when(repository.findDefaultByName(eq("Entertainment"))).thenReturn(Optional.empty());
        when(repository.findDefaultByName(eq("Healthcare"))).thenReturn(Optional.empty());
        when(repository.findDefaultByName(eq("Utilities"))).thenReturn(Optional.empty());
        when(repository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationRunner runner = seeder.seedDefaultCategories(repository);
        runner.run(null);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(repository, org.mockito.Mockito.times(5)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Category::getName)
                .containsExactlyInAnyOrder("Rent", "Transportation", "Entertainment", "Healthcare", "Utilities");
    }
}
