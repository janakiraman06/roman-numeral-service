package com.adobe.romannumeral.filter;

import com.adobe.romannumeral.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP filter for rate limiting API requests.
 * 
 * <p>This filter intercepts all requests to the /romannumeral endpoint and
 * applies per-IP rate limiting using the token bucket algorithm.</p>
 * 
 * <h2>Behavior:</h2>
 * <ul>
 *   <li>Allows requests if tokens are available</li>
 *   <li>Returns 429 Too Many Requests if limit exceeded</li>
 *   <li>Includes Retry-After header indicating wait time</li>
 * </ul>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@Component
@Order(1) // Execute early in filter chain
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitConfig rateLimitConfig;

    /**
     * Constructs the filter with rate limit configuration.
     * 
     * @param rateLimitConfig the rate limiting configuration
     */
    public RateLimitFilter(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // Only apply rate limiting to the API endpoint
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith("/romannumeral")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIP(request);
        Bucket bucket = rateLimitConfig.resolveBucket(clientIp);
        
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            // Request allowed - add rate limit headers
            response.addHeader("X-Rate-Limit-Remaining", 
                String.valueOf(probe.getRemainingTokens()));
            response.addHeader("X-Rate-Limit-Limit", 
                String.valueOf(rateLimitConfig.getRequestsPerMinute()));
            
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            long waitTimeSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            
            logger.warn("Rate limit exceeded for IP: {}. Wait time: {}s", 
                clientIp, waitTimeSeconds);
            
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.addHeader("Retry-After", String.valueOf(waitTimeSeconds));
            response.addHeader("X-Rate-Limit-Remaining", "0");
            response.addHeader("X-Rate-Limit-Limit", 
                String.valueOf(rateLimitConfig.getRequestsPerMinute()));
            
            response.getWriter().write(
                "Error: Rate limit exceeded. Please wait " + waitTimeSeconds + 
                " seconds before retrying. Limit: " + rateLimitConfig.getRequestsPerMinute() + 
                " requests per minute."
            );
        }
    }

    /**
     * Extracts the client IP address from the request.
     * 
     * <p>Checks X-Forwarded-For header first (for proxied requests),
     * then falls back to the remote address.</p>
     * 
     * @param request the HTTP request
     * @return the client IP address
     */
    private String getClientIP(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain (original client)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

