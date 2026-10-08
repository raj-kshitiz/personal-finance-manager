package com.pfm.dto.response;

import java.util.List;

/**
 * Wrapper for {@code GET /api/transactions}.
 *
 * @param transactions the matching transactions, newest first
 */
public record TransactionListResponse(List<TransactionResponse> transactions) {
}
