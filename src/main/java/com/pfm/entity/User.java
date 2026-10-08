package com.pfm.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * A registered application user. The {@code username} is the user's e-mail address and is always
 * stored lower-cased so that look-ups are case-insensitive.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Required by JPA. */
    protected User() {
    }

    /**
     * Creates a user.
     *
     * @param username     lower-cased e-mail address
     * @param passwordHash BCrypt hash of the password
     * @param fullName     the user's full name
     * @param phoneNumber  the user's phone number
     */
    public User(String username, String passwordHash, String fullName, String phoneNumber) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.createdAt = Instant.now();
    }

    /** @return the surrogate primary key */
    public Long getId() {
        return id;
    }

    /** @return the lower-cased e-mail address used as login name */
    public String getUsername() {
        return username;
    }

    /** @param username the lower-cased e-mail address */
    public void setUsername(String username) {
        this.username = username;
    }

    /** @return the BCrypt password hash */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** @param passwordHash the BCrypt password hash */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** @return the user's full name */
    public String getFullName() {
        return fullName;
    }

    /** @param fullName the user's full name */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /** @return the user's phone number */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /** @param phoneNumber the user's phone number */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /** @return when the account was created */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @param createdAt when the account was created */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** @param id the surrogate primary key (test support) */
    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
