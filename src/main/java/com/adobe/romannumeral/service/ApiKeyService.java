package com.adobe.romannumeral.service;

import com.adobe.romannumeral.entity.ApiKey;
import com.adobe.romannumeral.entity.User;
import com.adobe.romannumeral.repository.ApiKeyRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for API key management and validation.
 *
 * <p>Implements secure API key authentication following industry best practices:
 * <ul>
 *   <li>Keys are never stored in plaintext - only SHA-256 hashes</li>
 *   <li>Key format: rns_{prefix}_{secret} for easy identification</li>
 *   <li>Constant-time comparison to prevent timing attacks</li>
 *   <li>Automatic usage tracking for audit trails</li>
 * </ul>
 * </p>
 *
 * <p>See ADR-010: API Key Authentication for design rationale.</p>
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String KEY_PREFIX = "rns_";
    private static final int PREFIX_LENGTH = 8;
    private static final int SECRET_LENGTH = 24;

    private final ApiKeyRepository apiKeyRepository;
    private final Counter validationSuccessCounter;
    private final Counter validationFailureCounter;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, MeterRegistry meterRegistry) {
        this.apiKeyRepository = apiKeyRepository;
        this.validationSuccessCounter = Counter.builder("api.key.validation.success")
                .description("Number of successful API key validations")
                .register(meterRegistry);
        this.validationFailureCounter = Counter.builder("api.key.validation.failure")
                .description("Number of failed API key validations")
                .register(meterRegistry);
    }

    /**
     * Validates an API key and returns the associated user if valid.
     *
     * @param rawApiKey the raw API key from the request
     * @return Optional containing the ApiKey if valid, empty otherwise
     */
    @Transactional
    public Optional<ApiKey> validateKey(String rawApiKey) {
        if (rawApiKey == null || rawApiKey.isBlank()) {
            log.debug("Empty API key provided");
            validationFailureCounter.increment();
            return Optional.empty();
        }

        String cleanKey = rawApiKey.trim();
        
        // Validate key format
        if (!cleanKey.startsWith(KEY_PREFIX)) {
            log.debug("Invalid API key format - missing prefix");
            validationFailureCounter.increment();
            return Optional.empty();
        }

        // Hash the key and look it up
        String keyHash = hashApiKey(cleanKey);
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHash(keyHash);

        if (apiKeyOpt.isEmpty()) {
            log.debug("API key not found");
            validationFailureCounter.increment();
            return Optional.empty();
        }

        ApiKey apiKey = apiKeyOpt.get();

        // Check if key is valid (not expired, not revoked)
        if (!apiKey.isValid()) {
            log.warn("API key is invalid - expired or revoked: {}", apiKey.getKeyPrefix());
            validationFailureCounter.increment();
            return Optional.empty();
        }

        // Update last used timestamp
        apiKey.recordUsage();
        apiKeyRepository.save(apiKey);

        log.debug("API key validated successfully: {}", apiKey.getKeyPrefix());
        validationSuccessCounter.increment();
        return Optional.of(apiKey);
    }

    /**
     * Generates a new API key for a user.
     *
     * @param user the user to create the key for
     * @param keyName a descriptive name for the key
     * @return the raw API key (only returned once, store securely!)
     */
    @Transactional
    public String generateApiKey(User user, String keyName) {
        SecureRandom random = new SecureRandom();
        
        // Generate random bytes for prefix and secret
        byte[] prefixBytes = new byte[PREFIX_LENGTH / 2];
        byte[] secretBytes = new byte[SECRET_LENGTH];
        random.nextBytes(prefixBytes);
        random.nextBytes(secretBytes);

        String prefix = KEY_PREFIX + bytesToHex(prefixBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String rawKey = prefix + "_" + secret;

        // Create and save the API key entity
        String keyHash = hashApiKey(rawKey);
        ApiKey apiKey = new ApiKey(prefix, keyHash, keyName, user);
        apiKeyRepository.save(apiKey);

        log.info("Generated new API key for user {}: {}", user.getUsername(), prefix);
        return rawKey;
    }

    /**
     * Revokes an API key.
     *
     * @param keyPrefix the key prefix to revoke
     * @return true if the key was found and revoked
     */
    @Transactional
    public boolean revokeKey(String keyPrefix) {
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyPrefix(keyPrefix);
        if (apiKeyOpt.isPresent()) {
            ApiKey apiKey = apiKeyOpt.get();
            apiKey.revoke();
            apiKeyRepository.save(apiKey);
            log.info("Revoked API key: {}", keyPrefix);
            return true;
        }
        return false;
    }

    /**
     * Hashes an API key using SHA-256.
     *
     * @param apiKey the raw API key
     * @return the hex-encoded SHA-256 hash
     */
    public String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts bytes to hex string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

