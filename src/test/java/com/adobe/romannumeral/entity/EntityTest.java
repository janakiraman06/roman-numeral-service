package com.adobe.romannumeral.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Entity Tests")
class EntityTest {

    @Nested
    @DisplayName("User Entity")
    class UserTests {

        @Test
        @DisplayName("should create user with username")
        void shouldCreateUserWithUsername() {
            User user = new User("testuser");
            assertEquals("testuser", user.getUsername());
            assertTrue(user.isActive());
        }

        @Test
        @DisplayName("should create user with username and email")
        void shouldCreateUserWithUsernameAndEmail() {
            User user = new User("testuser", "test@example.com");
            assertEquals("testuser", user.getUsername());
            assertEquals("test@example.com", user.getEmail());
        }

        @Test
        @DisplayName("should update display name")
        void shouldUpdateDisplayName() {
            User user = new User("testuser");
            user.setDisplayName("Test User");
            assertEquals("Test User", user.getDisplayName());
        }

        @Test
        @DisplayName("should deactivate user")
        void shouldDeactivateUser() {
            User user = new User("testuser");
            user.setActive(false);
            assertFalse(user.isActive());
        }

        @Test
        @DisplayName("toString should contain username")
        void toStringShouldContainUsername() {
            User user = new User("testuser");
            assertTrue(user.toString().contains("testuser"));
        }
    }

    @Nested
    @DisplayName("ApiKey Entity")
    class ApiKeyTests {

        @Test
        @DisplayName("should create API key")
        void shouldCreateApiKey() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hashvalue", "Test Key", user);
            
            assertEquals("rns_test1234", key.getKeyPrefix());
            assertEquals("hashvalue", key.getKeyHash());
            assertEquals("Test Key", key.getName());
            assertEquals(user, key.getUser());
            assertTrue(key.isActive());
        }

        @Test
        @DisplayName("should be valid when active and not expired")
        void shouldBeValidWhenActiveAndNotExpired() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            assertTrue(key.isValid());
        }

        @Test
        @DisplayName("should be invalid when inactive")
        void shouldBeInvalidWhenInactive() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            key.setActive(false);
            assertFalse(key.isValid());
        }

        @Test
        @DisplayName("should be invalid when expired")
        void shouldBeInvalidWhenExpired() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            key.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
            assertFalse(key.isValid());
        }

        @Test
        @DisplayName("should be valid when expiration is in future")
        void shouldBeValidWhenExpirationInFuture() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            key.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
            assertTrue(key.isValid());
        }

        @Test
        @DisplayName("should revoke key")
        void shouldRevokeKey() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            
            key.revoke();
            
            assertFalse(key.isActive());
            assertNotNull(key.getRevokedAt());
            assertFalse(key.isValid());
        }

        @Test
        @DisplayName("should record usage")
        void shouldRecordUsage() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            assertNull(key.getLastUsedAt());
            
            key.recordUsage();
            
            assertNotNull(key.getLastUsedAt());
        }

        @Test
        @DisplayName("should set description")
        void shouldSetDescription() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            key.setDescription("API key for testing");
            assertEquals("API key for testing", key.getDescription());
        }

        @Test
        @DisplayName("should set rate limit override")
        void shouldSetRateLimitOverride() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            key.setRateLimitOverride(1000);
            assertEquals(1000, key.getRateLimitOverride());
        }

        @Test
        @DisplayName("toString should contain prefix and name")
        void toStringShouldContainPrefixAndName() {
            User user = new User("testuser");
            ApiKey key = new ApiKey("rns_test1234", "hash", "Test Key", user);
            String str = key.toString();
            assertTrue(str.contains("rns_test1234"));
            assertTrue(str.contains("Test Key"));
        }
    }

}

