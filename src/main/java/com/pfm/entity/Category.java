package com.pfm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * A transaction category. A category is either a system default (owner {@code null},
 * {@code custom == false}) visible to everybody, or a custom category owned by exactly one user.
 */
@Entity
@Table(name = "categories", indexes = {
        @Index(name = "idx_category_owner", columnList = "owner_id")
})
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CategoryType type;

    @Column(nullable = false)
    private boolean custom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    /** Required by JPA. */
    protected Category() {
    }

    /**
     * Creates a category.
     *
     * @param name   display name
     * @param type   INCOME or EXPENSE
     * @param custom {@code true} for a user-created category
     * @param owner  owning user, {@code null} for system defaults
     */
    public Category(String name, CategoryType type, boolean custom, User owner) {
        this.name = name;
        this.type = type;
        this.custom = custom;
        this.owner = owner;
    }

    /**
     * Creates a system default category.
     *
     * @param name display name
     * @param type INCOME or EXPENSE
     * @return the new default category
     */
    public static Category defaultCategory(String name, CategoryType type) {
        return new Category(name, type, false, null);
    }

    /** @return the surrogate primary key */
    public Long getId() {
        return id;
    }

    /** @param id the surrogate primary key (test support) */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return the canonical display name */
    public String getName() {
        return name;
    }

    /** @param name the canonical display name */
    public void setName(String name) {
        this.name = name;
    }

    /** @return INCOME or EXPENSE */
    public CategoryType getType() {
        return type;
    }

    /** @param type INCOME or EXPENSE */
    public void setType(CategoryType type) {
        this.type = type;
    }

    /** @return {@code true} when this category was created by a user */
    public boolean isCustom() {
        return custom;
    }

    /** @param custom {@code true} when this category was created by a user */
    public void setCustom(boolean custom) {
        this.custom = custom;
    }

    /** @return the owning user, or {@code null} for a system default */
    public User getOwner() {
        return owner;
    }

    /** @param owner the owning user, or {@code null} for a system default */
    public void setOwner(User owner) {
        this.owner = owner;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Category other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
