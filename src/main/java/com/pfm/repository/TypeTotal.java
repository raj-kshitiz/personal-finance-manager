package com.pfm.repository;

import com.pfm.entity.CategoryType;
import java.math.BigDecimal;

/**
 * A database-side aggregate of transaction amounts grouped by type only.
 *
 * @param type  INCOME or EXPENSE
 * @param total the summed amount, never {@code null}
 */
public record TypeTotal(CategoryType type, BigDecimal total) {
}
