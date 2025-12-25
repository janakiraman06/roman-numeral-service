package com.adobe.romannumeral.filter;

import com.adobe.romannumeral.entity.ApiKey;
import com.adobe.romannumeral.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * HTTP filter for API key authentication.
 *
 * <p>This filter validates API keys for requests to the /romannumeral endpoint
 * when API security is enabled. It supports multiple ways to provide the key:</p>
 *
 * <ul>
 *   <li>Header: X-API-Key: {key}</li>
 *   <li>Header: Authorization: Bearer {key}</li>
 *   <li>Query parameter: ?api_key={key} (not recommended for production)</li>
 * </ul>
 *
 * <h2>Design Decisions:</h2>
 * <ul>
 *   <li>Runs before rate limiting (Order 0) to reject unauthorized requests early</li>
 *   <li>Disabled in dev profile for easier local testing</li>
 *   <li>Adds user context to MDC for logging correlation</li>
 * </ul>
 *
 * <p>See ADR-010: API Key Authentication for design rationale.</p>
 */
@Component
@Order(0) // Execute before rate limiting
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_PARAM = "api_key";

    private final ApiKeyService apiKeyService;
    private final boolean securityEnabled;

    public ApiKeyAuthenticationFilter(
            ApiKeyService apiKeyService,
            @Value("${app.api-security.enabled:false}") boolean securityEnabled) {
        this.apiKeyService = apiKeyService;
        this.securityEnabled = securityEnabled;
        log.info("API Key Authentication Filter initialized. Security enabled: {}", securityEnabled);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip if security is disabled
        if (!securityEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestUri = request.getRequestURI();

        // Only require API key for the main API endpoint
        if (!requestUri.startsWith("/romannumeral")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract API key from request
        String apiKey = extractApiKey(request);

        if (apiKey == null) {
            log.warn("Missing API key for request: {} {}", request.getMethod(), requestUri);
            sendUnauthorizedResponse(response, "API key is required. "
                    + "Provide via X-API-Key header or Authorization: Bearer {key}");
            return;
        }

        // Validate the API key
        Optional<ApiKey> validatedKey = apiKeyService.validateKey(apiKey);

        if (validatedKey.isEmpty()) {
            log.warn("Invalid API key for request: {} {}", request.getMethod(), requestUri);
            sendUnauthorizedResponse(response, "Invalid or expired API key");
            return;
        }

        // Add user context to MDC for logging
        ApiKey key = validatedKey.get();
        MDC.put("userId", String.valueOf(key.getUser().getId()));
        MDC.put("apiKeyPrefix", key.getKeyPrefix());

        try {
            // Store validated key in request for downstream use
            request.setAttribute("authenticatedApiKey", key);
            request.setAttribute("authenticatedUser", key.getUser());

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
            MDC.remove("apiKeyPrefix");
        }
    }

    /**
     * Extracts API key from request headers or query parameters.
     *
     * <p>Priority order:
     * <ol>
     *   <li>X-API-Key header (preferred)</li>
     *   <li>Authorization: Bearer header</li>
     *   <li>api_key query parameter (fallback, less secure)</li>
     * </ol>
     * </p>
     */
    private String extractApiKey(HttpServletRequest request) {
        // Check X-API-Key header first (preferred)
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey.trim();
        }

        // Check Authorization header
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length()).trim();
        }

        // Fallback to query parameter (not recommended for production)
        String queryParam = request.getParameter(API_KEY_PARAM);
        if (queryParam != null && !queryParam.isBlank()) {
            log.debug("API key provided via query parameter - consider using headers instead");
            return queryParam.trim();
        }

        return null;
    }

    /**
     * Sends a 401 Unauthorized response with error message.
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) 
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.setHeader("WWW-Authenticate", "Bearer realm=\"roman-numeral-service\"");
        response.getWriter().write("Error: " + message);
    }

    /**
     * Returns whether API security is currently enabled.
     */
    public boolean isSecurityEnabled() {
        return securityEnabled;
    }
}

