package com.pfm.controller;

import com.pfm.dto.request.TransactionCreateRequest;
import com.pfm.dto.request.TransactionFilter;
import com.pfm.dto.request.TransactionUpdateRequest;
import com.pfm.dto.response.MessageResponse;
import com.pfm.dto.response.TransactionListResponse;
import com.pfm.dto.response.TransactionResponse;
import com.pfm.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for transaction management. Thin by design: all validation beyond {@code @Valid}
 * and all business rules live in {@link TransactionService}; errors are translated centrally by the
 * global exception handler.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Creates the controller.
     *
     * @param transactionService the transaction service
     */
    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * Creates a transaction owned by the authenticated caller.
     *
     * @param request the validated create payload
     * @return 201 with the created transaction
     */
    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionCreateRequest request) {
        TransactionResponse created = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists the authenticated caller's transactions, newest first, optionally filtered.
     *
     * @param startDate  inclusive lower date bound ({@code YYYY-MM-DD}), optional
     * @param endDate    inclusive upper date bound ({@code YYYY-MM-DD}), optional
     * @param categoryId exact category id, optional
     * @param category   category name, optional
     * @param type       {@code INCOME} or {@code EXPENSE}, case-insensitive, optional
     * @return 200 with the matching transactions
     */
    @GetMapping
    public ResponseEntity<TransactionListResponse> listTransactions(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type) {
        TransactionFilter filter = TransactionFilter.parse(startDate, endDate, categoryId, category, type);
        return ResponseEntity.ok(new TransactionListResponse(transactionService.listTransactions(filter)));
    }

    /**
     * Updates a transaction owned by the authenticated caller. Only the fields present in the
     * payload are applied; a {@code date} property in the body is ignored.
     *
     * @param id      the transaction id
     * @param request the validated update payload
     * @return 200 with the full updated transaction
     */
    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(@PathVariable Long id,
                                                                  @Valid @RequestBody TransactionUpdateRequest request) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, request));
    }

    /**
     * Permanently deletes a transaction owned by the authenticated caller.
     *
     * @param id the transaction id
     * @return 200 with a confirmation message
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.ok(new MessageResponse("Transaction deleted successfully"));
    }
}
