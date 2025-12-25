package com.adobe.romannumeral.config;

import com.adobe.romannumeral.entity.ApiKey;
import com.adobe.romannumeral.entity.User;
import com.adobe.romannumeral.repository.ApiKeyRepository;
import com.adobe.romannumeral.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Initializes demo data for development and testing.
 *
 * <p>This initializer creates:
 * <ul>
 *   <li>A demo user for testing API key authentication</li>
 *   <li>A demo API key that can be used for local development</li>
 * </ul>
 * </p>
 *
 * <p>Only runs in non-production profiles to avoid polluting production data.</p>
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * Demo API key for local testing.
     * In production, keys are generated securely and this is not used.
     */
    public static final String DEMO_API_KEY = "rns_demo1234_testkeyforlocaldev";

    /**
     * Initializes demo data for dev profile.
     */
    @Bean
    @Profile({"dev", "default"})
    CommandLineRunner initDemoData(UserRepository userRepository, ApiKeyRepository apiKeyRepository) {
        return args -> {
            // Check if demo user already exists
            if (userRepository.findByUsername("demo").isPresent()) {
                log.info("Demo data already exists, skipping initialization");
                return;
            }

            log.info("Initializing demo data for development...");

            // Create demo user
            User demoUser = new User("demo", "demo@example.com");
            demoUser.setDisplayName("Demo User");
            demoUser = userRepository.save(demoUser);
            log.info("Created demo user: {}", demoUser);

            // Create demo API key
            String keyHash = hashApiKey(DEMO_API_KEY);
            ApiKey demoKey = new ApiKey("rns_demo1234", keyHash, "Demo Key", demoUser);
            demoKey.setDescription("Demo API key for local development and testing");
            demoKey.setExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS));
            demoKey = apiKeyRepository.save(demoKey);
            log.info("Created demo API key: {}", demoKey);

            log.info("=".repeat(60));
            log.info("Demo API Key for testing: {}", DEMO_API_KEY);
            log.info("Use header: X-API-Key: {}", DEMO_API_KEY);
            log.info("=".repeat(60));
        };
    }

    /**
     * Hashes an API key using SHA-256.
     */
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}

