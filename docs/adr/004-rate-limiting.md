# ADR-004: Use Bucket4j for Rate Limiting

## Status

Accepted

## Date

2024-12-24

## Context

The API is publicly accessible without authentication (as per specification). To protect against abuse and ensure fair usage, we need rate limiting. Requirements:

- Prevent denial-of-service attacks
- Ensure fair resource distribution
- Provide clear feedback when limits exceeded
- Minimal latency impact

## Decision Drivers

- No external infrastructure dependency (no Redis required)
- Simple configuration
- Low latency overhead
- Standard HTTP rate limit headers
- Proven library with active maintenance

## Considered Options

### 1. Spring Cloud Gateway Rate Limiter

```yaml
spring:
  cloud:
    gateway:
      routes:
        - filters:
          - name: RequestRateLimiter
            args:
              redis-rate-limiter.replenishRate: 10
```

**Pros:**
- Built into Spring Cloud
- Redis-backed for distributed systems

**Cons:**
- Requires Spring Cloud Gateway
- Requires Redis
- Overkill for single-instance deployment

### 2. Resilience4j Rate Limiter

```java
@RateLimiter(name = "romanNumeral")
public ConversionResult convert(int number) { ... }
```

**Pros:**
- Part of Resilience4j ecosystem
- Annotation-based

**Cons:**
- Method-level, not request-level
- Less flexible for per-IP limiting
- Primarily designed for circuit breaking

### 3. Bucket4j (Chosen)

```java
Bucket bucket = Bucket.builder()
    .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1))))
    .build();

if (bucket.tryConsume(1)) {
    // Process request
} else {
    // Return 429
}
```

**Pros:**
- Purpose-built for rate limiting
- Token bucket algorithm
- No external dependencies
- Flexible (per-IP, global, etc.)
- Low latency
- Active maintenance

**Cons:**
- Additional dependency
- In-memory only (single instance)

### 4. Custom Implementation

```java
ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
```

**Pros:**
- No dependencies
- Full control

**Cons:**
- Reinventing the wheel
- Edge cases (memory leaks, race conditions)
- No standard algorithm

## Decision

**Use Bucket4j (Option 3).**

Bucket4j provides a production-ready token bucket implementation:

```java
@Bean
public Bucket rateLimitBucket() {
    return Bucket.builder()
        .addLimit(Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1))))
        .build();
}
```

Configuration:
- **100 requests per minute** per IP
- **Token bucket algorithm**: Smooth rate limiting
- **Greedy refill**: Tokens replenish continuously

## Consequences

### Positive

- **Battle-tested**: Widely used in production
- **Low latency**: ~1μs per check
- **Flexible**: Easy to adjust limits
- **Standard headers**: X-Rate-Limit-Remaining, Retry-After
- **No infrastructure**: No Redis/external store needed

### Negative

- **Single instance**: In-memory state not shared across instances
- **Memory usage**: One bucket per IP address
- **Cleanup needed**: Expired buckets need eviction

### Risks

- **Memory growth**: Many unique IPs could exhaust memory
- **Mitigation**: Implement bucket eviction or use bounded cache

## Implementation Details

### Filter Chain Integration

```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(...) {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", 
                String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setHeader("Retry-After", 
                String.valueOf(probe.getNanosToWaitForRefill() / 1_000_000_000));
            response.getWriter().write("Error: Too many requests.");
        }
    }
}
```

### Rate Limit Headers

| Header | Description | Example |
|--------|-------------|---------|
| X-Rate-Limit-Limit | Max requests per window | 100 |
| X-Rate-Limit-Remaining | Remaining requests | 95 |
| Retry-After | Seconds until refill | 30 |

## Scaling Considerations

For horizontal scaling, we would migrate to:

```java
// Redis-backed Bucket4j
ProxyManager<String> proxyManager = 
    Bucket4jRedis.casBasedBuilder(redissonClient).build();
```

This is a future enhancement if horizontal scaling is required.

## Compliance

- Protects API from abuse
- Provides standard rate limit headers
- Clear error messages when exceeded

## Notes

- Bucket4j GitHub: https://github.com/bucket4j/bucket4j
- Token bucket algorithm provides smooth rate limiting
- Current limit: 100 requests/minute (configurable)

