package com.pfm.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import com.pfm.util.MoneyUtil;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single transaction as returned by the API. {@code type} is derived from the category.
 *
 * @param id          the transaction id
 * @param amount      the amount, always scaled to two decimals
 * @param date        the transaction date
 * @param category    the canonical category name
 * @param description the optional description
 * @param type        INCOME or EXPENSE, derived from the category
 */
public record TransactionResponse(
        Long id,
        BigDecimal amount,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate date,
        String category,
        String description,
        CategoryType type) {

    /**
     * Maps an entity to its response DTO.
     *
     * @param transaction the entity
     * @return the response DTO
     */
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                MoneyUtil.scale(transaction.getAmount()),
                transaction.getDate(),
                transaction.getCategory().getName(),
                transaction.getDescription(),
                transaction.getType());
    }
}
