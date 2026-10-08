package com.pfm.controller;

import com.pfm.dto.request.CategoryCreateRequest;
import com.pfm.dto.response.CategoryListResponse;
import com.pfm.dto.response.CategoryResponse;
import com.pfm.dto.response.MessageResponse;
import com.pfm.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for category management. Thin by design: all validation beyond {@code @Valid} and
 * all business rules live in {@link CategoryService}; errors are translated centrally by the global
 * exception handler.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Creates the controller.
     *
     * @param categoryService the category service
     */
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Lists every category accessible to the authenticated caller.
     *
     * @return 200 with the default categories plus the caller's own custom categories
     */
    @GetMapping
    public ResponseEntity<CategoryListResponse> listCategories() {
        return ResponseEntity.ok(new CategoryListResponse(categoryService.listCategories()));
    }

    /**
     * Creates a custom category owned by the authenticated caller.
     *
     * @param request the validated create payload
     * @return 201 with the created category
     */
    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse created = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Deletes one of the authenticated caller's custom categories.
     *
     * <p>The name is mapped with a regex path variable so that names containing a dot or slash
     * (after URL-decoding) are not truncated by Spring's default path matching.</p>
     *
     * @param name the category name, case-insensitive, possibly URL-encoded
     * @return 200 with a confirmation message
     */
    @DeleteMapping("/{name:.+}")
    public ResponseEntity<MessageResponse> deleteCategory(@PathVariable String name) {
        categoryService.deleteCategory(name);
        return ResponseEntity.ok(new MessageResponse("Category deleted successfully"));
    }
}
