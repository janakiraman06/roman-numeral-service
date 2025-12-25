package com.adobe.romannumeral.filter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.adobe.romannumeral.entity.ApiKey;
import com.adobe.romannumeral.entity.User;
import com.adobe.romannumeral.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyAuthenticationFilter Tests")
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Nested
    @DisplayName("When Security Disabled")
    class SecurityDisabled {

        @Test
        @DisplayName("should allow all requests when security is disabled")
        void shouldAllowAllRequestsWhenDisabled() throws Exception {
            ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyService, false);
            request.setRequestURI("/romannumeral");
            
            filter.doFilterInternal(request, response, filterChain);
            
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(apiKeyService);
        }

        @Test
        @DisplayName("isSecurityEnabled should return false")
        void isSecurityEnabledShouldReturnFalse() {
            ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyService, false);
            assertFalse(filter.isSecurityEnabled());
        }
    }

    @Nested
    @DisplayName("When Security Enabled")
    class SecurityEnabled {

        private ApiKeyAuthenticationFilter filter;

        @BeforeEach
        void setUp() {
            filter = new ApiKeyAuthenticationFilter(apiKeyService, true);
        }

        @Test
        @DisplayName("isSecurityEnabled should return true")
        void isSecurityEnabledShouldReturnTrue() {
            assertTrue(filter.isSecurityEnabled());
        }

        @Test
        @DisplayName("should allow non-API endpoints without key")
        void shouldAllowNonApiEndpoints() throws Exception {
            request.setRequestURI("/swagger-ui.html");
            
            filter.doFilterInternal(request, response, filterChain);
            
            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(apiKeyService);
        }

        @Test
        @DisplayName("should allow actuator endpoints without key")
        void shouldAllowActuatorEndpoints() throws Exception {
            request.setRequestURI("/actuator/health");
            
            filter.doFilterInternal(request, response, filterChain);
            
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should return 401 when no API key provided")
        void shouldReturn401WhenNoKeyProvided() throws Exception {
            request.setRequestURI("/romannumeral");
            
            filter.doFilterInternal(request, response, filterChain);
            
            assertEquals(401, response.getStatus());
            assertTrue(response.getContentAsString().contains("API key is required"));
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("should return 401 for invalid API key")
        void shouldReturn401ForInvalidKey() throws Exception {
            request.setRequestURI("/romannumeral");
            request.addHeader("X-API-Key", "rns_invalid_key");
            when(apiKeyService.validateKey(anyString())).thenReturn(Optional.empty());
            
            filter.doFilterInternal(request, response, filterChain);
            
            assertEquals(401, response.getStatus());
            assertTrue(response.getContentAsString().contains("Invalid or expired"));
        }

        @Test
        @DisplayName("should allow request with valid X-API-Key header")
        void shouldAllowValidXApiKeyHeader() throws Exception {
            request.setRequestURI("/romannumeral");
            request.addHeader("X-API-Key", "rns_valid_key");
            
            User user = new User("testuser");
            ApiKey validKey = new ApiKey("rns_valid", "hash", "Test", user);
            when(apiKeyService.validateKey("rns_valid_key")).thenReturn(Optional.of(validKey));
            
            filter.doFilterInternal(request, response, filterChain);
            
            verify(filterChain).doFilter(request, response);
            assertEquals(validKey, request.getAttribute("authenticatedApiKey"));
            assertEquals(user, request.getAttribute("authenticatedUser"));
        }

        @Test
        @DisplayName("should allow request with valid Authorization Bearer header")
        void shouldAllowValidBearerHeader() throws Exception {
            request.setRequestURI("/romannumeral");
            request.addHeader("Authorization", "Bearer rns_bearer_key");
            
            User user = new User("testuser");
            ApiKey validKey = new ApiKey("rns_bearer", "hash", "Test", user);
            when(apiKeyService.validateKey("rns_bearer_key")).thenReturn(Optional.of(validKey));
            
            filter.doFilterInternal(request, response, filterChain);
            
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should allow request with valid query parameter")
        void shouldAllowValidQueryParameter() throws Exception {
            request.setRequestURI("/romannumeral");
            request.setParameter("api_key", "rns_query_key");
            
            User user = new User("testuser");
            ApiKey validKey = new ApiKey("rns_query", "hash", "Test", user);
            when(apiKeyService.validateKey("rns_query_key")).thenReturn(Optional.of(validKey));
            
            filter.doFilterInternal(request, response, filterChain);
            
            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("should prefer X-API-Key over Authorization header")
        void shouldPreferXApiKeyHeader() throws Exception {
            request.setRequestURI("/romannumeral");
            request.addHeader("X-API-Key", "rns_xapi_key");
            request.addHeader("Authorization", "Bearer rns_bearer_key");
            
            User user = new User("testuser");
            ApiKey validKey = new ApiKey("rns_xapi", "hash", "Test", user);
            when(apiKeyService.validateKey("rns_xapi_key")).thenReturn(Optional.of(validKey));
            
            filter.doFilterInternal(request, response, filterChain);
            
            verify(apiKeyService).validateKey("rns_xapi_key");
            verify(apiKeyService, never()).validateKey("rns_bearer_key");
        }

        @Test
        @DisplayName("should include WWW-Authenticate header on 401")
        void shouldIncludeWwwAuthenticateHeader() throws Exception {
            request.setRequestURI("/romannumeral");
            
            filter.doFilterInternal(request, response, filterChain);
            
            assertEquals(401, response.getStatus());
            assertNotNull(response.getHeader("WWW-Authenticate"));
            assertTrue(response.getHeader("WWW-Authenticate").contains("Bearer"));
        }
    }
}

