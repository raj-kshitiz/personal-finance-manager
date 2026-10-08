package com.pfm.service;

import com.pfm.dto.request.CategoryCreateRequest;
import com.pfm.dto.response.CategoryResponse;
import java.util.List;

/**
 * Category management. "Accessible" always means the system default categories plus the
 * authenticated user's own custom categories.
 */
public interface CategoryService {

    /**
     * Lists every category the authenticated user may use.
     *
     * @return default categories first, then the caller's custom ones
     */
    List<CategoryResponse> listCategories();

    /**
     * Creates a custom category owned by the authenticated user.
     *
     * @param request the validated payload; the name is trimmed before use
     * @return the created category with {@code isCustom = true}
     * @throws com.pfm.exception.ConflictException if the (trimmed, case-insensitive) name clashes
     *                                             with a system default category or with one of the
     *                                             caller's own custom categories. The same name
     *                                             owned by a different user is not a conflict.
     */
    CategoryResponse createCategory(CategoryCreateRequest request);

    /**
     * Deletes one of the authenticated user's custom categories, addressed by name.
     *
     * @param name the category name, case-insensitive; may arrive URL-encoded
     * @throws com.pfm.exception.ForbiddenOperationException if {@code name} is a system default
     *                                                       category
     * @throws com.pfm.exception.ResourceNotFoundException   if the caller owns no custom category
     *                                                       with that name
     * @throws com.pfm.exception.BadRequestException         if at least one transaction references
     *                                                       the category
     */
    void deleteCategory(String name);
}
