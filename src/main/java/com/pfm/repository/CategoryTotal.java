package com.pfm.repository;

import com.pfm.entity.CategoryType;
import java.math.BigDecimal;

/**
 * A database-side aggregate of transaction amounts grouped by category name and type.
 *
 * @param categoryName the canonical category name
 * @param type         INCOME or EXPENSE
 * @param total        the summed amount, never {@code null}
 */
public record CategoryTotal(String categoryName, CategoryType type, BigDecimal total) {
}
