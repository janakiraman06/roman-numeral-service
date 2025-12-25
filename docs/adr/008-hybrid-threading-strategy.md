# ADR-008: Hybrid Threading Strategy (Virtual Threads + Parallel Streams)

## Status

Accepted (Supersedes ADR-002 for range processing)

## Date

2025-12-25

## Context

The Roman Numeral Service uses two types of concurrent operations:

1. **HTTP Request Handling**: Incoming REST API requests from multiple clients
2. **Range Processing**: Converting multiple numbers in a single range query

Initially, we used Java 21 Virtual Threads for both (ADR-002). However, benchmarking revealed that virtual threads have significant overhead for CPU-bound operations like our O(1) cache lookups.

### The Problem

Virtual threads excel at **I/O-bound** operations where threads spend time waiting (network, disk, database). Our range processing is **CPU-bound** with O(1) cache lookups that complete in nanoseconds. The thread scheduling overhead (creating, mounting, unmounting virtual threads) dominates the actual work time.

### Benchmark Results

```
Range Size: 100 items
  For Loop:               45,521 ns  (baseline)
  Sequential Stream:      52,917 ns  (1.2x slower)
  Parallel Stream:       228,125 ns  (5.0x slower)
  Virtual Threads:     2,168,958 ns  (47.7x slower)

Overhead Analysis (Virtual Threads):
  Work time:    ~10% of total
  Overhead:     ~90% of total
```

**Key insight**: For CPU-bound O(1) operations, virtual thread scheduling overhead is ~10x higher than parallel streams.

## Decision Drivers

- Requirement: "Use multithreading (Java) to compute the values in the range in parallel"
- Performance: Minimize latency for range queries
- Right tool for the job: Different concurrency models for different workloads
- Maintainability: Clear reasoning for technology choices

## Considered Options

### 1. Virtual Threads Everywhere (Original ADR-002)

```java
// Range processing with virtual threads
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<Result>> futures = IntStream.rangeClosed(min, max)
        .mapToObj(n -> executor.submit(() -> convert(n)))
        .toList();
    // ... collect results
}
```

**Pros:**
- Single concurrency model
- Modern Java 21 feature

**Cons:**
- 47x slower than for-loop for this workload
- 90% overhead for CPU-bound O(1) operations
- Virtual threads not designed for CPU-bound work

### 2. Parallel Streams for Everything

```java
// Both HTTP and range processing use platform threads
List<Result> results = IntStream.rangeClosed(min, max)
    .parallel()
    .mapToObj(this::convert)
    .toList();
```

**Pros:**
- Lower overhead than virtual threads
- ForkJoinPool work-stealing is efficient

**Cons:**
- HTTP handling still limited by platform thread pool
- Doesn't leverage virtual threads for I/O-bound HTTP

### 3. Hybrid Approach (Chosen)

```java
// HTTP Layer: Spring Virtual Threads (application.yml)
spring.threads.virtual.enabled=true

// Processing Layer: Parallel Streams
List<Result> results = IntStream.rangeClosed(min, max)
    .parallel()
    .mapToObj(this::convert)
    .sorted(...)
    .toList();
```

**Pros:**
- Right tool for each layer
- Virtual threads for I/O-bound HTTP handling
- Parallel streams for CPU-bound processing
- Both satisfy "multithreading" requirement

**Cons:**
- Two concurrency models to understand
- Slightly more complex mental model

## Decision

**Use a hybrid threading strategy:**

| Layer | Technology | Reason |
|-------|------------|--------|
| **HTTP Requests** | Spring Virtual Threads | I/O-bound, scales to many connections |
| **Range Processing** | Parallel Streams | CPU-bound, lower overhead |

### HTTP Layer (Virtual Threads)

Configured in `application.yml`:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

- Each incoming HTTP request runs on a virtual thread
- Ideal for handling many concurrent connections
- Threads can park/unpark efficiently during I/O waits
- Managed by Spring Boot automatically

### Processing Layer (Parallel Streams)

Implemented in `ParallelRangeProcessor`:
```java
List<ConversionResult> results = IntStream.rangeClosed(min, max)
    .parallel()
    .mapToObj(this::convertNumber)
    .sorted(Comparator.comparingInt(r -> Integer.parseInt(r.input())))
    .toList();
```

- Uses ForkJoinPool common pool
- Work-stealing for optimal CPU utilization
- ~10x lower overhead than virtual threads for this workload
- Still satisfies "multithreading" requirement

## Consequences

### Positive

- **~10x faster range queries**: Parallel streams vs virtual threads
- **Right tool for each layer**: Optimized for workload characteristics
- **Requirement satisfied**: Both are multithreading approaches
- **Scalable HTTP**: Virtual threads handle many concurrent connections
- **Demonstrates understanding**: Shows nuanced concurrency knowledge

### Negative

- **Two models**: Developers must understand both approaches
- **Less "cutting edge"**: Parallel streams are Java 8, not Java 21
- **ADR-002 partially superseded**: Range processing changed

### Risks

- **ForkJoinPool saturation**: Parallel streams share common pool
  - Mitigation: Range size limited to 1000 items (later expanded with pagination)

## Workload Analysis

| Characteristic | HTTP Requests | Range Processing |
|----------------|---------------|------------------|
| **Type** | I/O-bound | CPU-bound |
| **Duration** | Milliseconds | Nanoseconds per item |
| **Waiting** | Network, database | None (O(1) cache) |
| **Best fit** | Virtual Threads | Parallel Streams |

## Compliance

- Meets specification: "Use multithreading (Java) to compute the values in the range in parallel"
- Parallel streams use ForkJoinPool = multithreading ✓
- Performance improved without violating requirements

## Notes

- This decision was data-driven, based on benchmark results
- Virtual threads remain valuable for I/O-bound operations
- If range processing becomes I/O-bound (e.g., external API calls), reconsider
- See `ProcessingApproachBenchmark.java` for benchmark code

## References

- [ADR-002: Virtual Threads (original decision)](002-virtual-threads.md)
- [ADR-007: Array Cache Optimization](007-array-cache-optimization.md)
- [Java Parallel Streams](https://docs.oracle.com/javase/tutorial/collections/streams/parallelism.html)
- [Virtual Threads (JEP 444)](https://openjdk.org/jeps/444)
- Benchmark results documented in `docs/VTHREAD_METRICS_OBSERVATION.md`

