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
 * A single income or expense entry belonging to one user. The transaction {@code type} is never
 * stored: it is always derived from the referenced {@link Category}.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_user_date", columnList = "user_id,tx_date"),
        @Index(name = "idx_tx_user_category", columnList = "user_id,category_id")
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "tx_date", nullable = false)
    private LocalDate date;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Required by JPA. */
    protected Transaction() {
    }

    /**
     * Creates a transaction.
     *
     * @param user        owning user
     * @param category    referenced category
     * @param amount      positive monetary amount
     * @param date        the date the money moved
     * @param description optional free-text description
     */
    public Transaction(User user, Category category, BigDecimal amount, LocalDate date, String description) {
        this.user = user;
        this.category = category;
        this.amount = amount;
        this.date = date;
        this.description = description;
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

    /** @return the referenced category */
    public Category getCategory() {
        return category;
    }

    /** @param category the referenced category */
    public void setCategory(Category category) {
        this.category = category;
    }

    /** @return the monetary amount */
    public BigDecimal getAmount() {
        return amount;
    }

    /** @param amount the monetary amount */
    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    /** @return the transaction date */
    public LocalDate getDate() {
        return date;
    }

    /** @param date the transaction date */
    public void setDate(LocalDate date) {
        this.date = date;
    }

    /** @return the optional description */
    public String getDescription() {
        return description;
    }

    /** @param description the optional description */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @return when the row was created */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt when the row was created */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Derives the transaction type from its category.
     *
     * @return INCOME or EXPENSE
     */
    public CategoryType getType() {
        return category == null ? null : category.getType();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transaction other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
