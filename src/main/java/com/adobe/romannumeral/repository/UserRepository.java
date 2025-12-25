package com.adobe.romannumeral.repository;

import com.adobe.romannumeral.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for User entity operations.
 *
 * <p>Follows Spring Data JPA conventions for automatic query generation.
 * Custom queries can be added as needed for analytics.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique username.
     *
     * @param username the username to search for
     * @return the user if found
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by their email address.
     *
     * @param email the email to search for
     * @return the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if a username already exists.
     *
     * @param username the username to check
     * @return true if username exists
     */
    boolean existsByUsername(String username);

    /**
     * Checks if an email already exists.
     *
     * @param email the email to check
     * @return true if email exists
     */
    boolean existsByEmail(String email);
}

