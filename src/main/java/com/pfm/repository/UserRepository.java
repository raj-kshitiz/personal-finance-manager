package com.pfm.repository;

import com.pfm.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Data access for {@link User}. Usernames are persisted lower-cased, so callers must lower-case
 * the argument before calling these methods.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by exact (already lower-cased) username.
     *
     * @param username lower-cased e-mail address
     * @return the user, if present
     */
    Optional<User> findByUsername(String username);

    /**
     * Checks whether a username is taken.
     *
     * @param username lower-cased e-mail address
     * @return {@code true} if a user with that username exists
     */
    boolean existsByUsername(String username);
}
