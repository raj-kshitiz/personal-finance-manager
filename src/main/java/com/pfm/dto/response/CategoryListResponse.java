package com.pfm.dto.response;

import java.util.List;

/**
 * Wrapper for {@code GET /api/categories}.
 *
 * @param categories the accessible categories
 */
public record CategoryListResponse(List<CategoryResponse> categories) {
}
