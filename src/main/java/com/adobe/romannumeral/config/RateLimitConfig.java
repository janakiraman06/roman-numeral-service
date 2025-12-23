package com.adobe.romannumeral.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for API rate limiting using Bucket4j.
 * 
 * <p>This configuration implements token bucket rate limiting to prevent
 * API abuse and ensure fair usage across clients.</p>
 * 
 * <h2>Rate Limit Strategy:</h2>
 * <ul>
 *   <li>Per-IP rate limiting</li>
 *   <li>Default: 100 requests per minute</li>
 *   <li>Token bucket algorithm with greedy refill</li>
 * </ul>
 * 
 * <h2>Design Decision:</h2>
 * <p>Rate limiting provides DoS protection without requiring authentication.
 * This is appropriate for a public utility API like this conversion service.</p>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@Configuration
public class RateLimitConfig {

    @Value("${app.rate-limiting.requests-per-minute:100}")
    private int requestsPerMinute;

    /**
     * In-memory cache of rate limit buckets per client IP.
     * In production with multiple instances, use Redis-backed buckets.
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Gets or creates a rate limit bucket for the given client key (IP address).
     * 
     * @param clientKey the client identifier (usually IP address)
     * @return the Bucket for rate limiting this client
     */
    public Bucket resolveBucket(String clientKey) {
        return buckets.computeIfAbsent(clientKey, this::createNewBucket);
    }

    /**
     * Creates a new rate limit bucket with configured capacity.
     * 
     * <h3>Token Bucket Configuration:</h3>
     * <ul>
     *   <li>Capacity: requestsPerMinute tokens</li>
     *   <li>Refill: Greedy refill of all tokens every minute</li>
     * </ul>
     * 
     * @param clientKey the client identifier (unused, but required for computeIfAbsent)
     * @return a new Bucket configured for rate limiting
     */
    private Bucket createNewBucket(String clientKey) {
        Bandwidth limit = Bandwidth.classic(
            requestsPerMinute,
            Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
        );
        
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    /**
     * Returns the configured requests per minute limit.
     * 
     * @return the rate limit
     */
    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }
}

