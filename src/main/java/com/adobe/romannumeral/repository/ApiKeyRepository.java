package com.adobe.romannumeral.repository;

import com.adobe.romannumeral.entity.ApiKey;
import com.adobe.romannumeral.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ApiKey entity operations.
 *
 * <p>Provides secure API key lookup and management operations.
 * Note: Always use keyHash for lookups, never store or query plaintext keys.</p>
 */
@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /**
     * Finds an API key by its SHA-256 hash.
     * This is the primary lookup method for authentication.
     *
     * @param keyHash the SHA-256 hash of the API key
     * @return the API key if found
     */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Finds all API keys for a specific user.
     *
     * @param user the user to find keys for
     * @return list of API keys
     */
    List<ApiKey> findByUser(User user);

    /**
     * Finds all active API keys for a user.
     *
     * @param user the user to find keys for
     * @return list of active API keys
     */
    List<ApiKey> findByUserAndActiveTrue(User user);

    /**
     * Finds an API key by its prefix (for display/identification).
     *
     * @param keyPrefix the key prefix (e.g., "rns_abc123")
     * @return the API key if found
     */
    Optional<ApiKey> findByKeyPrefix(String keyPrefix);

    /**
     * Checks if a key hash already exists.
     *
     * @param keyHash the hash to check
     * @return true if hash exists
     */
    boolean existsByKeyHash(String keyHash);

    /**
     * Updates the lastUsedAt timestamp for a key.
     * Uses a dedicated query for efficiency.
     *
     * @param id the key ID
     * @param timestamp the timestamp to set
     */
    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :timestamp WHERE k.id = :id")
    void updateLastUsedAt(@Param("id") Long id, @Param("timestamp") java.time.Instant timestamp);

    /**
     * Counts active keys for a user (for key limit enforcement).
     *
     * @param user the user to count keys for
     * @return count of active keys
     */
    long countByUserAndActiveTrue(User user);
}

