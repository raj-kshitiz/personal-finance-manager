package com.pfm.service.impl;

import com.pfm.dto.request.CategoryCreateRequest;
import com.pfm.dto.response.CategoryResponse;
import com.pfm.entity.Category;
import com.pfm.exception.BadRequestException;
import com.pfm.exception.ConflictException;
import com.pfm.exception.ForbiddenOperationException;
import com.pfm.exception.ResourceNotFoundException;
import com.pfm.repository.CategoryRepository;
import com.pfm.security.CurrentUserProvider;
import com.pfm.service.CategoryService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Default {@link CategoryService} implementation. "Accessible" categories are the system defaults
 * (owner {@code null}) plus the authenticated caller's own custom categories.
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Creates the service.
     *
     * @param categoryRepository  data access for categories
     * @param currentUserProvider resolves the authenticated caller
     */
    public CategoryServiceImpl(CategoryRepository categoryRepository, CurrentUserProvider currentUserProvider) {
        this.categoryRepository = categoryRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CategoryResponse> listCategories() {
        Long userId = currentUserProvider.getCurrentUserId();
        return categoryRepository.findAccessible(userId).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        String name = request.name().trim();
        Long userId = currentUserProvider.getCurrentUserId();

        if (categoryRepository.findDefaultByName(name).isPresent()) {
            throw new ConflictException("A default category named '" + name + "' already exists");
        }
        if (categoryRepository.findCustomByOwnerAndName(userId, name).isPresent()) {
            throw new ConflictException("You already have a category named '" + name + "'");
        }

        Category category = new Category(name, request.type(), true, currentUserProvider.getCurrentUser());
        Category saved = categoryRepository.save(category);
        return CategoryResponse.from(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteCategory(String name) {
        Long userId = currentUserProvider.getCurrentUserId();

        if (categoryRepository.findDefaultByName(name).isPresent()) {
            throw new ForbiddenOperationException("Default category '" + name + "' cannot be deleted");
        }

        Category category = categoryRepository.findCustomByOwnerAndName(userId, name)
                .orElseThrow(() -> new ResourceNotFoundException("Category '" + name + "' not found"));

        if (categoryRepository.isReferencedByTransactions(category.getId())) {
            throw new BadRequestException(
                    "Category '" + name + "' cannot be deleted because it is referenced by existing transactions");
        }

        categoryRepository.delete(category);
    }
}
