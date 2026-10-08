package com.pfm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A savings goal owned by one user. Progress is never stored; it is recomputed from the user's
 * transactions on every read.
 */
@Entity
@Table(name = "savings_goals", indexes = {
        @Index(name = "idx_goal_user", columnList = "user_id")
})
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "goal_name", nullable = false, length = 100)
    private String goalName;

    @Column(name = "target_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Required by JPA. */
    protected SavingsGoal() {
    }

    /**
     * Creates a savings goal.
     *
     * @param user         owning user
     * @param goalName     descriptive identifier
     * @param targetAmount positive target amount
     * @param targetDate   future target date
     * @param startDate    date from which progress is accumulated
     */
    public SavingsGoal(User user, String goalName, BigDecimal targetAmount, LocalDate targetDate, LocalDate startDate) {
        this.user = user;
        this.goalName = goalName;
        this.targetAmount = targetAmount;
        this.targetDate = targetDate;
        this.startDate = startDate;
        this.createdAt = Instant.now();
    }

    /** @return the surrogate primary key */
    public Long getId() {
        return id;
    }

    /** @param id the surrogate primary key (test support) */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return the owning user */
    public User getUser() {
        return user;
    }

    /** @param user the owning user */
    public void setUser(User user) {
        this.user = user;
    }

    /** @return the goal name */
    public String getGoalName() {
        return goalName;
    }

    /** @param goalName the goal name */
    public void setGoalName(String goalName) {
        this.goalName = goalName;
    }

    /** @return the target amount */
    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    /** @param targetAmount the target amount */
    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    /** @return the target date */
    public LocalDate getTargetDate() {
        return targetDate;
    }

    /** @param targetDate the target date */
    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    /** @return the date from which progress is accumulated */
    public LocalDate getStartDate() {
        return startDate;
    }

    /** @param startDate the date from which progress is accumulated */
    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    /** @return when the row was created */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt when the row was created */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SavingsGoal other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
