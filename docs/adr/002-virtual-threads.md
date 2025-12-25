# ADR-002: Use Java 21 Virtual Threads for Parallel Processing

## Status

Partially Superseded by [ADR-008](008-hybrid-threading-strategy.md)

**Note**: Virtual threads are still used for HTTP request handling (Spring Boot).
Range processing has been changed to use Parallel Streams (see ADR-008).

## Date

2024-12-24

## Context

The API specification requires support for **range conversion** with parallel processing:

```
GET /romannumeral?min=1&max=100
```

This endpoint should convert a range of integers to Roman numerals using multithreading. We need to choose a concurrency model that:

- Demonstrates modern Java concurrency knowledge
- Scales efficiently with range size
- Avoids complex thread pool configuration
- Handles resource exhaustion gracefully

## Decision Drivers

- Specification requirement for multithreading
- Need to demonstrate Java 21 knowledge
- Simplicity over complex thread pool tuning
- Scalability for varying range sizes

## Considered Options

### 1. Fixed Thread Pool

```java
ExecutorService executor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);
```

**Pros:**
- Well-understood model
- Predictable resource usage
- Works on all Java versions

**Cons:**
- Requires pool size tuning
- Thread context switching overhead
- Platform threads are heavy (~1MB stack each)

### 2. ForkJoinPool (Parallel Streams)

```java
IntStream.rangeClosed(min, max)
    .parallel()
    .mapToObj(this::convert)
    .toList();
```

**Pros:**
- Simple API
- Work-stealing for load balancing
- Built into Java

**Cons:**
- Uses common pool (shared with other operations)
- Less control over execution
- Not ideal for I/O-bound tasks

### 3. Virtual Threads (Chosen)

```java
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<ConversionResult>> futures = IntStream.rangeClosed(min, max)
        .mapToObj(n -> executor.submit(() -> convert(n)))
        .toList();
}
```

**Pros:**
- Lightweight (~1KB vs 1MB for platform threads)
- No pool size configuration needed
- Scales to thousands of concurrent tasks
- Java 21 feature (demonstrates modern knowledge)
- Automatic cleanup with try-with-resources

**Cons:**
- Requires Java 21+
- Relatively new (GA in Java 21)
- Overkill for CPU-bound tasks (our case)

## Decision

**Use Java 21 Virtual Threads (Option 3).**

Virtual threads provide:

- **Simplicity**: No thread pool configuration
- **Scalability**: Handles thousands of concurrent conversions
- **Modern Practice**: Demonstrates Java 21 knowledge
- **Clean Code**: try-with-resources for automatic shutdown

```java
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<ConversionResult>> futures = IntStream.rangeClosed(min, max)
        .mapToObj(number -> executor.submit(() -> convertNumber(number)))
        .toList();
    
    return futures.stream()
        .map(this::getResultSafely)
        .sorted(Comparator.comparingInt(r -> Integer.parseInt(r.input())))
        .toList();
}
```

## Consequences

### Positive

- **No tuning required**: Virtual threads scale automatically
- **Demonstrates expertise**: Shows knowledge of Java 21 features
- **Future-proof**: Aligns with Java's direction for concurrency
- **Simple code**: Cleaner than thread pool management
- **Resource efficient**: Thousands of virtual threads use minimal memory

### Negative

- **Java 21 dependency**: Cannot run on older JVMs
- **Slight overhead**: Virtual threads have scheduling overhead
- **CPU-bound limitation**: Our O(1) lookups don't benefit from I/O parallelism

### Risks

- **New technology**: Less production experience in the ecosystem
- **Debugging**: Virtual thread stack traces can be complex

## Trade-off Analysis

While our Roman numeral conversion is O(1) (CPU-bound, not I/O-bound), we chose virtual threads because:

1. **Specification compliance**: Requirement asks for multithreading
2. **Skill demonstration**: Shows knowledge of cutting-edge Java features
3. **Simplicity**: Cleaner code than managing thread pools
4. **Scalability patterns**: Establishes patterns for future enhancements

## Resource Protection

To prevent resource exhaustion, we limit range size:

```java
private static final int MAX_RANGE_SIZE = 1000;

if (rangeSize > MAX_RANGE_SIZE) {
    throw new IllegalArgumentException("Range too large");
}
```

## Compliance

- Meets specification requirement for parallel processing
- Demonstrates Java 21 competency
- Includes resource protection against abuse

## Notes

- Virtual threads were incubated in Java 19-20, GA in Java 21
- JEP 444: Virtual Threads specification
- Spring Boot 3.2+ has native virtual thread support

