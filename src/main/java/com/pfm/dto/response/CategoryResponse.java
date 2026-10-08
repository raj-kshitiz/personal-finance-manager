package com.pfm.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;

/**
 * A single category as returned by the API.
 *
 * <p>The assignment's example body uses the property name {@code isCustom}. The official test
 * script instead greps for {@code "custom":true}. Both property names are therefore emitted and
 * always carry the same value.</p>
 */
public final class CategoryResponse {

    private final Long id;
    private final String name;
    private final CategoryType type;
    private final boolean custom;

    /**
     * Creates a response DTO.
     *
     * @param id     the category id
     * @param name   the canonical category name
     * @param type   INCOME or EXPENSE
     * @param custom {@code true} for a user-created category
     */
    public CategoryResponse(Long id, String name, CategoryType type, boolean custom) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.custom = custom;
    }

    /**
     * Maps an entity to its response DTO.
     *
     * @param category the entity
     * @return the response DTO
     */
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType(), category.isCustom());
    }

    /** @return the category id */
    @JsonProperty("id")
    public Long getId() {
        return id;
    }

    /** @return the canonical category name */
    @JsonProperty("name")
    public String getName() {
        return name;
    }

    /** @return INCOME or EXPENSE */
    @JsonProperty("type")
    public CategoryType getType() {
        return type;
    }

    /** @return {@code true} for a user-created category (assignment spelling) */
    @JsonProperty("isCustom")
    public boolean getIsCustom() {
        return custom;
    }

    /** @return {@code true} for a user-created category (test-script spelling) */
    @JsonProperty("custom")
    public boolean getCustom() {
        return custom;
    }
}
