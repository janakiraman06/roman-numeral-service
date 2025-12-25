package com.adobe.romannumeral.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.adobe.romannumeral.entity.ApiKey;
import com.adobe.romannumeral.entity.User;
import com.adobe.romannumeral.repository.ApiKeyRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyService Tests")
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private MeterRegistry meterRegistry;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        apiKeyService = new ApiKeyService(apiKeyRepository, meterRegistry);
    }

    @Nested
    @DisplayName("Key Validation")
    class KeyValidation {

        @Test
        @DisplayName("should return empty for null key")
        void shouldReturnEmptyForNullKey() {
            Optional<ApiKey> result = apiKeyService.validateKey(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for blank key")
        void shouldReturnEmptyForBlankKey() {
            Optional<ApiKey> result = apiKeyService.validateKey("   ");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for key without rns_ prefix")
        void shouldReturnEmptyForKeyWithoutPrefix() {
            Optional<ApiKey> result = apiKeyService.validateKey("invalid_key_format");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty when key not found in database")
        void shouldReturnEmptyWhenKeyNotFound() {
            when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.empty());
            
            Optional<ApiKey> result = apiKeyService.validateKey("rns_test1234_secretkey");
            
            assertTrue(result.isEmpty());
            verify(apiKeyRepository).findByKeyHash(any());
        }

        @Test
        @DisplayName("should return empty for expired key")
        void shouldReturnEmptyForExpiredKey() {
            User user = new User("testuser");
            ApiKey expiredKey = new ApiKey("rns_test1234", "hash", "Test Key", user);
            expiredKey.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
            
            when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(expiredKey));
            
            Optional<ApiKey> result = apiKeyService.validateKey("rns_test1234_secretkey");
            
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for revoked key")
        void shouldReturnEmptyForRevokedKey() {
            User user = new User("testuser");
            ApiKey revokedKey = new ApiKey("rns_test1234", "hash", "Test Key", user);
            revokedKey.revoke();
            
            when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(revokedKey));
            
            Optional<ApiKey> result = apiKeyService.validateKey("rns_test1234_secretkey");
            
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return empty for inactive key")
        void shouldReturnEmptyForInactiveKey() {
            User user = new User("testuser");
            ApiKey inactiveKey = new ApiKey("rns_test1234", "hash", "Test Key", user);
            inactiveKey.setActive(false);
            
            when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(inactiveKey));
            
            Optional<ApiKey> result = apiKeyService.validateKey("rns_test1234_secretkey");
            
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("should return key for valid active key")
        void shouldReturnKeyForValidActiveKey() {
            User user = new User("testuser");
            ApiKey validKey = new ApiKey("rns_test1234", "hash", "Test Key", user);
            
            when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(validKey));
            when(apiKeyRepository.save(any())).thenReturn(validKey);
            
            Optional<ApiKey> result = apiKeyService.validateKey("rns_test1234_secretkey");
            
            assertTrue(result.isPresent());
            assertEquals("rns_test1234", result.get().getKeyPrefix());
            verify(apiKeyRepository).save(any()); // Updates last used
        }

        @Test
        @DisplayName("should return key for valid key with future expiration")
        void shouldReturnKeyForValidKeyWithFutureExpiration() {
            User user = new User("testuser");
            ApiKey validKey = new ApiKey("rns_test1234", "hash", "Test Key", user);
            validKey.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
            
            when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(validKey));
            when(apiKeyRepository.save(any())).thenReturn(validKey);
            
            Optional<ApiKey> result = apiKeyService.validateKey("rns_test1234_secretkey");
            
            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Key Generation")
    class KeyGeneration {

        @Test
        @DisplayName("should generate key with correct prefix format")
        void shouldGenerateKeyWithCorrectPrefix() {
            User user = new User("testuser");
            when(apiKeyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            
            String rawKey = apiKeyService.generateApiKey(user, "Test Key");
            
            assertNotNull(rawKey);
            assertTrue(rawKey.startsWith("rns_"));
            assertTrue(rawKey.contains("_"));
            verify(apiKeyRepository).save(any(ApiKey.class));
        }
    }

    @Nested
    @DisplayName("Key Revocation")
    class KeyRevocation {

        @Test
        @DisplayName("should revoke existing key")
        void shouldRevokeExistingKey() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            
            when(apiKeyRepository.findByKeyPrefix("rns_test1234")).thenReturn(Optional.of(key));
            when(apiKeyRepository.save(any())).thenReturn(key);
            
            boolean result = apiKeyService.revokeKey("rns_test1234");
            
            assertTrue(result);
            assertFalse(key.isActive());
            verify(apiKeyRepository).save(key);
        }

        @Test
        @DisplayName("should return false for non-existent key")
        void shouldReturnFalseForNonExistentKey() {
            when(apiKeyRepository.findByKeyPrefix("rns_nonexist")).thenReturn(Optional.empty());
            
            boolean result = apiKeyService.revokeKey("rns_nonexist");
            
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Key Hashing")
    class KeyHashing {

        @Test
        @DisplayName("should produce consistent hash for same input")
        void shouldProduceConsistentHash() {
            String hash1 = apiKeyService.hashApiKey("test_key");
            String hash2 = apiKeyService.hashApiKey("test_key");
            
            assertEquals(hash1, hash2);
        }

        @Test
        @DisplayName("should produce different hash for different input")
        void shouldProduceDifferentHash() {
            String hash1 = apiKeyService.hashApiKey("test_key_1");
            String hash2 = apiKeyService.hashApiKey("test_key_2");
            
            assertNotEquals(hash1, hash2);
        }

        @Test
        @DisplayName("should produce 64-character hex hash")
        void shouldProduce64CharHash() {
            String hash = apiKeyService.hashApiKey("test_key");
            
            assertEquals(64, hash.length());
            assertTrue(hash.matches("[0-9a-f]+"));
        }
    }

    @Nested
    @DisplayName("Metrics")
    class Metrics {

        @Test
        @DisplayName("should increment failure counter on invalid key")
        void shouldIncrementFailureCounter() {
            apiKeyService.validateKey(null);
            apiKeyService.validateKey("");
            apiKeyService.validateKey("invalid");
            
            Counter failureCounter = meterRegistry.find("api.key.validation.failure").counter();
            assertNotNull(failureCounter);
            assertEquals(3.0, failureCounter.count());
        }
    }
}

