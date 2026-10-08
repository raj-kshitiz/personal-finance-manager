package com.pfm.dto.request;

import com.pfm.entity.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create payload for {@code POST /api/categories}.
 *
 * @param name mandatory name, 1-50 characters after trimming
 * @param type mandatory, {@code INCOME} or {@code EXPENSE}
 */
public record CategoryCreateRequest(
        @NotBlank(message = "name is required")
        @Size(min = 1, max = 50, message = "name must be between 1 and 50 characters")
        String name,

        @NotNull(message = "type is required and must be INCOME or EXPENSE")
        CategoryType type) {
}
