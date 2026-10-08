package com.pfm.repository;

import com.pfm.entity.CategoryType;
import com.pfm.entity.Transaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link Transaction}. Every aggregate is computed in the database so that deleted
 * rows automatically disappear from goal progress and reports.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Single-query filtered search. All filter arguments are optional; passing {@code null} means
     * "do not filter on this attribute". Results are sorted newest first (date descending, then id
     * descending).
     *
     * @param userId     the authenticated user's id
     * @param startDate  inclusive lower date bound, or {@code null}
     * @param endDate    inclusive upper date bound, or {@code null}
     * @param categoryId exact category id, or {@code null}
     * @param type       INCOME or EXPENSE, or {@code null}
     * @return the matching transactions
     */
    @Query("""
            select t from Transaction t
            join fetch t.category c
            where t.user.id = :userId
              and (:startDate is null or t.date >= :startDate)
              and (:endDate is null or t.date <= :endDate)
              and (:categoryId is null or c.id = :categoryId)
              and (:type is null or c.type = :type)
            order by t.date desc, t.id desc
            """)
    List<Transaction> search(@Param("userId") Long userId,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate,
                            @Param("categoryId") Long categoryId,
                            @Param("type") CategoryType type);

    /**
     * Finds a transaction by id regardless of owner; the service applies the ownership rule.
     *
     * @param id the transaction id
     * @return the transaction, if present
     */
    @Override
    Optional<Transaction> findById(Long id);

    /**
     * Sums a user's transaction amounts per type within an inclusive date range.
     *
     * @param userId    the authenticated user's id
     * @param startDate inclusive lower bound
     * @param endDate   inclusive upper bound
     * @return one row per type that has at least one transaction
     */
    @Query("""
            select new com.pfm.repository.TypeTotal(c.type, sum(t.amount))
            from Transaction t join t.category c
            where t.user.id = :userId and t.date >= :startDate and t.date <= :endDate
            group by c.type
            """)
    List<TypeTotal> sumByType(@Param("userId") Long userId,
                             @Param("startDate") LocalDate startDate,
                             @Param("endDate") LocalDate endDate);

    /**
     * Sums a user's transaction amounts per category name and type within an inclusive date range.
     *
     * @param userId    the authenticated user's id
     * @param startDate inclusive lower bound
     * @param endDate   inclusive upper bound
     * @return one row per (category, type) that has at least one transaction, ordered by name
     */
    @Query("""
            select new com.pfm.repository.CategoryTotal(c.name, c.type, sum(t.amount))
            from Transaction t join t.category c
            where t.user.id = :userId and t.date >= :startDate and t.date <= :endDate
            group by c.name, c.type
            order by c.name asc
            """)
    List<CategoryTotal> sumByCategory(@Param("userId") Long userId,
                                     @Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);

    /**
     * Checks whether the given category is referenced by at least one of the user's transactions.
     *
     * @param userId     the authenticated user's id
     * @param categoryId the category id
     * @return {@code true} when in use
     */
    @Query("select count(t) > 0 from Transaction t where t.user.id = :userId and t.category.id = :categoryId")
    boolean existsByUserIdAndCategoryId(@Param("userId") Long userId, @Param("categoryId") Long categoryId);
}
