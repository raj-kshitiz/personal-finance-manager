package com.pfm.service;

import com.pfm.dto.request.TransactionCreateRequest;
import com.pfm.dto.request.TransactionFilter;
import com.pfm.dto.request.TransactionUpdateRequest;
import com.pfm.dto.response.TransactionResponse;
import java.util.List;

/**
 * Transaction CRUD for the authenticated user. Every operation is scoped to the caller: a
 * transaction owned by another user is indistinguishable from one that does not exist (404).
 */
public interface TransactionService {

    /**
     * Creates a transaction.
     *
     * @param request the validated payload
     * @return the created transaction, with {@code type} derived from the category
     * @throws com.pfm.exception.BadRequestException if the date is in the future (relative to the
     *                                               application {@code Clock}), or the category
     *                                               name does not resolve to a category accessible
     *                                               to the caller
     */
    TransactionResponse createTransaction(TransactionCreateRequest request);

    /**
     * Lists the caller's transactions, newest first (date descending, then id descending).
     *
     * @param filter parsed query filters; components may be {@code null}
     * @return the matching transactions
     * @throws com.pfm.exception.ResourceNotFoundException if a {@code categoryId} filter refers to
     *                                                     a category that does not exist or is not
     *                                                     accessible to the caller
     * @throws com.pfm.exception.BadRequestException       if a {@code category} name filter does
     *                                                     not resolve to an accessible category
     */
    List<TransactionResponse> listTransactions(TransactionFilter filter);

    /**
     * Updates a transaction. Only the fields present in the payload are applied; a {@code date}
     * property in the request body is ignored and the stored date is preserved.
     *
     * @param id      the transaction id
     * @param request the validated payload
     * @return the full updated transaction
     * @throws com.pfm.exception.ResourceNotFoundException if the transaction does not exist or
     *                                                     belongs to another user
     * @throws com.pfm.exception.BadRequestException       if a supplied category is not accessible
     */
    TransactionResponse updateTransaction(Long id, TransactionUpdateRequest request);

    /**
     * Permanently deletes a transaction so that it disappears from goal progress and reports.
     *
     * @param id the transaction id
     * @throws com.pfm.exception.ResourceNotFoundException if the transaction does not exist or
     *                                                     belongs to another user
     */
    void deleteTransaction(Long id);
}
