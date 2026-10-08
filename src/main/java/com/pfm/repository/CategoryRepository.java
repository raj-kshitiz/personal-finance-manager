package com.pfm.repository;

import com.pfm.entity.Category;
import com.pfm.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link Category}. "Accessible" always means: the system default categories plus
 * the custom categories owned by the given user.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Lists every category the user may use, defaults first then custom ones, each alphabetically.
     *
     * @param userId the authenticated user's id
     * @return accessible categories
     */
    @Query("""
            select c from Category c
            where c.owner is null or c.owner.id = :userId
            order by c.custom asc, c.name asc
            """)
    List<Category> findAccessible(@Param("userId") Long userId);

    /**
     * Finds an accessible category by case-insensitive name.
     *
     * @param userId the authenticated user's id
     * @param name   the category name in any casing
     * @return the matching category, if any
     */
    @Query("""
            select c from Category c
            where (c.owner is null or c.owner.id = :userId)
              and lower(c.name) = lower(:name)
            """)
    Optional<Category> findAccessibleByName(@Param("userId") Long userId, @Param("name") String name);

    /**
     * Finds an accessible category by id.
     *
     * @param userId the authenticated user's id
     * @param id     the category id
     * @return the matching category, if any
     */
    @Query("""
            select c from Category c
            where (c.owner is null or c.owner.id = :userId) and c.id = :id
            """)
    Optional<Category> findAccessibleById(@Param("userId") Long userId, @Param("id") Long id);

    /**
     * Finds a system default category by case-insensitive name.
     *
     * @param name the category name in any casing
     * @return the matching default category, if any
     */
    @Query("select c from Category c where c.owner is null and lower(c.name) = lower(:name)")
    Optional<Category> findDefaultByName(@Param("name") String name);

    /**
     * Finds a custom category owned by the given user, by case-insensitive name.
     *
     * @param userId the owning user's id
     * @param name   the category name in any casing
     * @return the matching custom category, if any
     */
    @Query("""
            select c from Category c
            where c.owner.id = :userId and lower(c.name) = lower(:name)
            """)
    Optional<Category> findCustomByOwnerAndName(@Param("userId") Long userId, @Param("name") String name);

    /**
     * Checks whether any transaction (of any user) references the category.
     *
     * @param categoryId the category id
     * @return {@code true} when the category is in use
     */
    @Query("select count(t) > 0 from Transaction t where t.category.id = :categoryId")
    boolean isReferencedByTransactions(@Param("categoryId") Long categoryId);

    /**
     * Lists all system default categories.
     *
     * @return the default categories
     */
    @Query("select c from Category c where c.owner is null")
    List<Category> findAllDefaults();
}
