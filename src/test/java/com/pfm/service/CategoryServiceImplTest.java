package com.pfm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pfm.dto.request.CategoryCreateRequest;
import com.pfm.dto.response.CategoryResponse;
import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.entity.User;
import com.pfm.exception.BadRequestException;
import com.pfm.exception.ConflictException;
import com.pfm.exception.ForbiddenOperationException;
import com.pfm.exception.ResourceNotFoundException;
import com.pfm.repository.CategoryRepository;
import com.pfm.security.CurrentUserProvider;
import com.pfm.service.impl.CategoryServiceImpl;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Mockito unit tests for {@link CategoryServiceImpl}, covering every business rule documented on
 * {@link CategoryService}.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    private static final Long USER_ID = 42L;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private CategoryServiceImpl service;
    private User caller;

    @BeforeEach
    void setUp() {
        service = new CategoryServiceImpl(categoryRepository, currentUserProvider);
        caller = new User("user@example.com", "hash", "User One", "+911234567890");
        setId(caller, USER_ID);
    }

    private static void setId(User user, Long id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setId(Category category, Long id) {
        category.setId(id);
    }

    @Test
    void listCategoriesReturnsDefaultsAndOwnCustomsMappedCorrectly() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);

        Category salary = Category.defaultCategory("Salary", CategoryType.INCOME);
        setId(salary, 1L);
        Category food = Category.defaultCategory("Food", CategoryType.EXPENSE);
        setId(food, 2L);
        Category custom = new Category("Freelance", CategoryType.INCOME, true, caller);
        setId(custom, 10L);

        when(categoryRepository.findAccessible(USER_ID)).thenReturn(List.of(salary, food, custom));

        List<CategoryResponse> result = service.listCategories();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("Salary");
        assertThat(result.get(0).getIsCustom()).isFalse();
        assertThat(result.get(0).getCustom()).isFalse();
        assertThat(result.get(1).getName()).isEqualTo("Food");
        assertThat(result.get(2).getName()).isEqualTo("Freelance");
        assertThat(result.get(2).getIsCustom()).isTrue();
        assertThat(result.get(2).getCustom()).isTrue();
        assertThat(result.get(2).getId()).isEqualTo(10L);
    }

    @Test
    void createCategorySucceedsWhenNameIsFree() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        when(categoryRepository.findDefaultByName("Freelance")).thenReturn(Optional.empty());
        when(categoryRepository.findCustomByOwnerAndName(USER_ID, "Freelance")).thenReturn(Optional.empty());

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        when(categoryRepository.save(captor.capture())).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            setId(c, 99L);
            return c;
        });

        CategoryResponse response = service.createCategory(
                new CategoryCreateRequest("Freelance", CategoryType.INCOME));

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getName()).isEqualTo("Freelance");
        assertThat(response.getIsCustom()).isTrue();
        assertThat(response.getCustom()).isTrue();

        Category saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Freelance");
        assertThat(saved.getOwner()).isEqualTo(caller);
        assertThat(saved.isCustom()).isTrue();
    }

    @Test
    void createCategoryTrimsNameBeforeCheckingAndStoring() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(currentUserProvider.getCurrentUser()).thenReturn(caller);
        when(categoryRepository.findDefaultByName("Freelance")).thenReturn(Optional.empty());
        when(categoryRepository.findCustomByOwnerAndName(USER_ID, "Freelance")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = service.createCategory(
                new CategoryCreateRequest("  Freelance  ", CategoryType.INCOME));

        assertThat(response.getName()).isEqualTo("Freelance");
        verify(categoryRepository).findDefaultByName("Freelance");
        verify(categoryRepository).findCustomByOwnerAndName(USER_ID, "Freelance");
    }

    @Test
    void createCategoryConflictsWithDefaultNameCaseInsensitively() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findDefaultByName("food"))
                .thenReturn(Optional.of(Category.defaultCategory("Food", CategoryType.EXPENSE)));

        assertThatThrownBy(() -> service.createCategory(new CategoryCreateRequest("food", CategoryType.EXPENSE)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("food");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategoryConflictsWithOwnExistingCustomCategoryCaseInsensitively() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findDefaultByName("FREELANCE")).thenReturn(Optional.empty());
        when(categoryRepository.findCustomByOwnerAndName(USER_ID, "FREELANCE"))
                .thenReturn(Optional.of(new Category("Freelance", CategoryType.INCOME, true, caller)));

        assertThatThrownBy(() -> service.createCategory(new CategoryCreateRequest("FREELANCE", CategoryType.INCOME)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("FREELANCE");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void deleteCategoryRejectsDefaultCategoryWithForbidden() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findDefaultByName("Food"))
                .thenReturn(Optional.of(Category.defaultCategory("Food", CategoryType.EXPENSE)));

        assertThatThrownBy(() -> service.deleteCategory("Food"))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Food");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategoryRejectsUnknownCategoryWithNotFound() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findDefaultByName("Unknown")).thenReturn(Optional.empty());
        when(categoryRepository.findCustomByOwnerAndName(USER_ID, "Unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCategory("Unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Unknown");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategoryRejectsInUseCategoryWithBadRequest() {
        Category custom = new Category("GymMembership", CategoryType.EXPENSE, true, caller);
        setId(custom, 7L);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findDefaultByName("GymMembership")).thenReturn(Optional.empty());
        when(categoryRepository.findCustomByOwnerAndName(USER_ID, "GymMembership")).thenReturn(Optional.of(custom));
        when(categoryRepository.isReferencedByTransactions(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteCategory("GymMembership"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("GymMembership")
                .hasMessageContaining("referenced by existing transactions");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void deleteCategorySucceedsAndInvokesRepositoryDelete() {
        Category custom = new Category("Freelance", CategoryType.INCOME, true, caller);
        setId(custom, 11L);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(categoryRepository.findDefaultByName("Freelance")).thenReturn(Optional.empty());
        when(categoryRepository.findCustomByOwnerAndName(USER_ID, "Freelance")).thenReturn(Optional.of(custom));
        when(categoryRepository.isReferencedByTransactions(11L)).thenReturn(false);

        service.deleteCategory("Freelance");

        verify(categoryRepository, times(1)).delete(custom);
    }
}
