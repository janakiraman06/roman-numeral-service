package com.adobe.romannumeral.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * API Key entity for authentication and authorization.
 *
 * <p>Design follows security best practices:
 * <ul>
 *   <li>Keys are stored as SHA-256 hashes (never plaintext)</li>
 *   <li>Prefix stored separately for key lookup without exposing hash</li>
 *   <li>Expiration support for key rotation policies</li>
 *   <li>Revocation with timestamp for audit trail</li>
 *   <li>Rate limit override per key for tiered access</li>
 * </ul>
 * </p>
 *
 * <p>The key format follows the pattern: {@code rns_<prefix>_<secret>}
 * where 'rns' is the service identifier, prefix is 8 chars for lookup,
 * and secret is the remaining hash input.</p>
 */
@Entity
@Table(name = "api_keys", indexes = {
    @Index(name = "idx_api_keys_key_hash", columnList = "keyHash", unique = true),
    @Index(name = "idx_api_keys_prefix", columnList = "keyPrefix"),
    @Index(name = "idx_api_keys_user_id", columnList = "user_id")
})
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * First 8 characters of the key for identification in logs/UI.
     * Format: "rns_xxxx" where xxxx is the prefix.
     */
    @Column(nullable = false, length = 12)
    private String keyPrefix;

    /**
     * SHA-256 hash of the full API key. Never store plaintext keys.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String keyHash;

    /**
     * Human-readable name for the key (e.g., "Production Server", "CI/CD").
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Optional description of the key's purpose.
     */
    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Optional expiration date. Null means no expiration.
     */
    @Column
    private Instant expiresAt;

    /**
     * When the key was revoked. Null means not revoked.
     */
    @Column
    private Instant revokedAt;

    /**
     * Last time the key was used for authentication.
     */
    @Column
    private Instant lastUsedAt;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Optional rate limit override. Null uses default rate limit.
     */
    @Column
    private Integer rateLimitOverride;

    // Default constructor for JPA
    protected ApiKey() {
    }

    public ApiKey(String keyPrefix, String keyHash, String name, User user) {
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.name = name;
        this.user = user;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Checks if the key is currently valid for authentication.
     *
     * @return true if key is active, not revoked, and not expired
     */
    public boolean isValid() {
        if (!active || revokedAt != null) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Revokes this API key immediately.
     */
    public void revoke() {
        this.active = false;
        this.revokedAt = Instant.now();
    }

    /**
     * Records that the key was used for authentication.
     */
    public void recordUsage() {
        this.lastUsedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getRateLimitOverride() {
        return rateLimitOverride;
    }

    public void setRateLimitOverride(Integer rateLimitOverride) {
        this.rateLimitOverride = rateLimitOverride;
    }

    @Override
    public String toString() {
        return "ApiKey{id=" + id + ", prefix='" + keyPrefix + "', name='" + name + "', active=" + active + "}";
    }
}

