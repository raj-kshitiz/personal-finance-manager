package com.pfm.service.impl;

import com.pfm.dto.request.TransactionCreateRequest;
import com.pfm.dto.request.TransactionFilter;
import com.pfm.dto.request.TransactionUpdateRequest;
import com.pfm.dto.response.TransactionResponse;
import com.pfm.entity.Category;
import com.pfm.entity.Transaction;
import com.pfm.entity.User;
import com.pfm.exception.BadRequestException;
import com.pfm.exception.ResourceNotFoundException;
import com.pfm.repository.CategoryRepository;
import com.pfm.repository.TransactionRepository;
import com.pfm.security.CurrentUserProvider;
import com.pfm.service.TransactionService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Default {@link TransactionService} implementation. Every operation is scoped to the authenticated
 * caller via {@link CurrentUserProvider}; "today" is always read from the injected {@link Clock} so
 * that behaviour is deterministic in tests and immune to server time zone quirks.
 */
@Service
public class TransactionServiceImpl implements TransactionService {

    /**
     * Policy flag documenting that a {@code date} property sent on a {@code PUT
     * /api/transactions/{id}} request body is silently ignored rather than rejected or applied.
     * This is forced by the official test script (SCENARIO 2.4) and recorded in
     * {@code docs/CONTRACT.md} &sect;5.1, overriding the older "reject with 400" wording in
     * {@code docs/BUILD_SPEC.md}. {@link TransactionUpdateRequest} intentionally has no {@code date}
     * component (and Jackson is configured to ignore unknown properties), so there is nothing to
     * strip here &mdash; this constant exists purely so the policy, and the one place to change it
     * if it ever changes, is documented in one place.
     */
    private static final boolean IGNORE_DATE_ON_UPDATE = true;

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param transactionRepository data access for transactions
     * @param categoryRepository    data access for categories
     * @param currentUserProvider   resolves the authenticated caller
     * @param clock                 the application clock, used for every "today" computation
     */
    public TransactionServiceImpl(TransactionRepository transactionRepository, CategoryRepository categoryRepository,
                                  CurrentUserProvider currentUserProvider, Clock clock) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionResponse createTransaction(TransactionCreateRequest request) {
        User user = currentUserProvider.getCurrentUser();

        if (request.date().isAfter(LocalDate.now(clock))) {
            throw new BadRequestException("date must not be in the future");
        }

        Category category = resolveCategoryByName(user.getId(), request.category());

        Transaction transaction = new Transaction(user, category, request.amount(), request.date(),
                request.description());
        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.from(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<TransactionResponse> listTransactions(TransactionFilter filter) {
        Long userId = currentUserProvider.getCurrentUserId();

        Long categoryId = null;
        if (filter.categoryId() != null) {
            Category category = categoryRepository.findAccessibleById(userId, filter.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category " + filter.categoryId() + " not found"));
            categoryId = category.getId();
        }
        if (filter.categoryName() != null) {
            Long byName = resolveCategoryByName(userId, filter.categoryName()).getId();
            // Both filters may be supplied. Rather than letting one silently win, require them to
            // agree, so a client never receives rows that contradict the filter it asked for.
            if (categoryId != null && !categoryId.equals(byName)) {
                throw new BadRequestException(
                        "categoryId and category refer to different categories");
            }
            categoryId = byName;
        }

        return transactionRepository
                .search(userId, filter.startDate(), filter.endDate(), categoryId, filter.type())
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TransactionResponse updateTransaction(Long id, TransactionUpdateRequest request) {
        Long userId = currentUserProvider.getCurrentUserId();
        Transaction transaction = findOwned(id, userId);

        // See IGNORE_DATE_ON_UPDATE: TransactionUpdateRequest has no date component by design, so
        // any "date" property present in the raw JSON body is already dropped by Jackson before
        // bean validation ever runs, and the stored date below is never touched.
        if (request.amount() != null) {
            transaction.setAmount(request.amount());
        }
        if (request.category() != null) {
            Category category = resolveCategoryByName(userId, request.category());
            transaction.setCategory(category);
        }
        if (request.description() != null) {
            transaction.setDescription(request.description());
        }

        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.from(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteTransaction(Long id) {
        Long userId = currentUserProvider.getCurrentUserId();
        Transaction transaction = findOwned(id, userId);
        transactionRepository.delete(transaction);
    }

    /**
     * Resolves a category name to an accessible {@link Category}, case-insensitively.
     *
     * @param userId the authenticated user's id
     * @param name   the category name
     * @return the resolved category
     * @throws BadRequestException if the name does not resolve to an accessible category
     */
    private Category resolveCategoryByName(Long userId, String name) {
        return categoryRepository.findAccessibleByName(userId, name)
                .orElseThrow(() -> new BadRequestException("Category '" + name + "' not found"));
    }

    /**
     * Finds a transaction and verifies it belongs to the given user, without leaking whether a
     * transaction owned by someone else exists.
     *
     * @param id     the transaction id
     * @param userId the authenticated user's id
     * @return the owned transaction
     * @throws ResourceNotFoundException if the transaction does not exist or belongs to another user
     */
    private Transaction findOwned(Long id, Long userId) {
        return transactionRepository.findById(id)
                .filter(transaction -> transaction.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction " + id + " not found"));
    }
}
