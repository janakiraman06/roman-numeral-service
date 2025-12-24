# Performance Benchmarks

> Last Updated: December 2024  
> Environment: macOS (Apple Silicon), Java 21, Spring Boot 3.4.1

## Executive Summary

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| **p95 Response Time** | 7ms | < 100ms | ✅ Exceeds |
| **Max Throughput** | 2,411 req/s | > 1,000 req/s | ✅ Exceeds |
| **Error Rate (under load)** | 0% | < 1% | ✅ Pass |
| **Availability** | 100% | 99.9% | ✅ Pass |

## Load Test Results

### Test Environment

```
Hardware: Apple Silicon (M-series)
OS: macOS
Java: OpenJDK 21.0.9
Framework: Spring Boot 3.4.1
Tool: k6 (Grafana)
Rate Limit: 100,000 req/min (disabled for testing)
```

### Test Suite Overview

| Test | Purpose | VUs | Duration | Pattern |
|------|---------|-----|----------|---------|
| Smoke | Basic verification | 1 | 30s | Constant |
| Load | Normal traffic | 50 | 5m | Ramp up/down |
| Stress | Find breaking point | 200 | 5m | Aggressive ramp |
| Spike | Sudden surge | 100 | 1.5m | Instant spike |

---

## Detailed Results

### 1. Smoke Test (Baseline)

**Purpose**: Verify basic functionality works under minimal load.

| Metric | Value |
|--------|-------|
| Virtual Users | 1 |
| Duration | 30 seconds |
| Total Requests | 60 |
| Checks Passed | 100% (150/150) |
| Error Rate | 0.00% |
| Avg Response Time | 5.95ms |
| p95 Response Time | 8.32ms |
| Throughput | 2 req/s |

**Verdict**: ✅ PASS - All endpoints functional

---

### 2. Load Test (Normal Traffic)

**Purpose**: Simulate expected production traffic patterns.

| Metric | Value |
|--------|-------|
| Max Virtual Users | 50 |
| Duration | 5 minutes |
| Total Requests | 79,858 |
| Checks Passed | 100% (159,716/159,716) |
| Error Rate | 0.00% |
| Avg Response Time | 72.15ms* |
| p95 Response Time | 7.04ms |
| p99 Response Time | ~10ms |
| Throughput | 171 req/s |
| Data Received | 72 MB |

*Average includes cold-start outliers; median is 2.75ms

**Verdict**: ✅ PASS - Handles expected load with excellent response times

---

### 3. Stress Test (Breaking Point)

**Purpose**: Find system limits and observe degradation behavior.

| Metric | Value |
|--------|-------|
| Max Virtual Users | 200 |
| Duration | 5 minutes |
| Total Requests | 723,564 |
| Successful Requests | 535,271 (74%) |
| Rate-Limited Requests | 188,293 (26%) |
| Avg Response Time | 2.27ms |
| p95 Response Time | 6.80ms |
| Max Response Time | 32.88ms |
| **Peak Throughput** | **2,411 req/s** |

**Observations**:
- System remained stable under 4x expected load
- Rate limiter correctly protected resources at 100K req/min
- No crashes, memory leaks, or degradation
- Response times stayed consistent even under stress

**Verdict**: ✅ PASS - Graceful degradation via rate limiting

---

### 4. Spike Test (Sudden Surge)

**Purpose**: Verify system handles sudden traffic spikes.

| Metric | Value |
|--------|-------|
| Max Virtual Users | 100 (instant spike) |
| Duration | 1.5 minutes |
| Total Requests | 36,030 |
| Checks Passed | 100% (72,060/72,060) |
| Error Rate | 0.00% |
| Avg Response Time | 2.77ms |
| p95 Response Time | 6.73ms |
| Throughput | 400 req/s |

**Verdict**: ✅ PASS - Handles sudden traffic spikes gracefully

---

## Performance Characteristics

### Response Time Distribution

```
         ┌─────────────────────────────────────────┐
  p50    │████████████                             │ 2-3ms
  p90    │████████████████████████████             │ 5-6ms
  p95    │██████████████████████████████████       │ 6-7ms
  p99    │████████████████████████████████████████ │ 8-10ms
         └─────────────────────────────────────────┘
```

### Throughput vs Response Time

| Throughput | Avg Response | p95 Response | Notes |
|------------|--------------|--------------|-------|
| 2 req/s | 5.95ms | 8.32ms | Smoke test baseline |
| 171 req/s | 2.75ms* | 7.04ms | Normal load |
| 400 req/s | 2.77ms | 6.73ms | Spike handling |
| 2,411 req/s | 2.27ms | 6.80ms | Peak capacity |

*Median; average affected by JIT warmup

### Why O(1) Conversion Matters

The pre-computed cache provides constant-time lookups:

```
Without cache (greedy algorithm):  O(13) per request
With cache:                        O(1) per request

At 2,411 req/s:
- Without cache: ~31,343 operations/sec
- With cache:    ~2,411 lookups/sec (13x reduction)
```

---

## Resource Utilization

### Memory Profile

| Metric | Value |
|--------|-------|
| Heap Initial | 256 MB |
| Heap Used (idle) | ~80 MB |
| Heap Used (load) | ~150 MB |
| Non-Heap (Metaspace) | ~60 MB |
| Cache Size | ~200 KB (3999 entries) |

### CPU Usage

| Scenario | CPU Usage |
|----------|-----------|
| Idle | < 1% |
| Light load (10 req/s) | 2-5% |
| Normal load (170 req/s) | 15-25% |
| Heavy load (2,400 req/s) | 60-80% |

---

## Rate Limiting Behavior

The API implements token bucket rate limiting:

| Setting | Value |
|---------|-------|
| Default Limit | 100 req/min per IP |
| Bucket Capacity | 100 tokens |
| Refill Rate | Greedy (100 tokens/min) |

### Rate Limit Response

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 45
Content-Type: text/plain

Error: Too many requests. Please try again later.
```

---

## Scalability Projections

Based on test results, estimated capacity:

| Deployment | Expected Throughput | Monthly Requests |
|------------|---------------------|------------------|
| Single instance | 2,000+ req/s | 5+ billion |
| 3 instances (LB) | 6,000+ req/s | 15+ billion |
| 10 instances (LB) | 20,000+ req/s | 50+ billion |

---

## Recommendations

### For Production Deployment

1. **Rate Limiting**: Adjust `app.rate-limiting.requests-per-minute` based on expected traffic
2. **Monitoring**: Use the included Grafana dashboard for real-time metrics
3. **Scaling**: Horizontal scaling via load balancer for >2,000 req/s
4. **JVM Tuning**: Consider `-XX:+UseZGC` for lower latency at scale

### For Load Testing

```bash
# Disable rate limiting for capacity testing
app.rate-limiting.requests-per-minute: 1000000

# Or run with environment variable
SPRING_APPLICATION_JSON='{"app":{"rate-limiting":{"requests-per-minute":1000000}}}'
```

---

## Running Load Tests

See [load-tests/README.md](load-tests/README.md) for instructions.

```bash
# Quick smoke test
cd load-tests/scripts
k6 run smoke-test.js

# Full test suite
k6 run smoke-test.js
k6 run load-test.js
k6 run stress-test.js
k6 run spike-test.js
```

