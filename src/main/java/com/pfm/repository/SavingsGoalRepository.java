package com.pfm.repository;

import com.pfm.entity.SavingsGoal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link SavingsGoal}.
 */
@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    /**
     * Lists a user's goals, oldest first.
     *
     * @param userId the owning user's id
     * @return the user's goals
     */
    @Query("select g from SavingsGoal g where g.user.id = :userId order by g.id asc")
    List<SavingsGoal> findByUserId(@Param("userId") Long userId);

    /**
     * Finds a goal by id regardless of owner. Ownership is checked by the service so that the API
     * can distinguish 404 (no such goal) from 403 (another user's goal).
     *
     * @param id the goal id
     * @return the goal, if present
     */
    @Override
    Optional<SavingsGoal> findById(Long id);
}
