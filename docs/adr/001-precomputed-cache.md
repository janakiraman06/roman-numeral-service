# ADR-001: Use Pre-computed Cache for Roman Numeral Conversion

## Status

Accepted

## Date

2024-12-24

## Context

The Roman Numeral Service needs to convert integers (1-3999) to Roman numerals. This is a core operation that will be called frequently. We need to decide on the conversion strategy that balances:

- **Performance**: Low latency for API responses
- **Memory**: Reasonable memory footprint
- **Complexity**: Maintainable and testable code
- **Thread Safety**: Safe for concurrent access

The conversion algorithm itself is deterministic—the same input always produces the same output. The valid input range is finite (3,999 values).

## Decision Drivers

- API response latency target: < 10ms p99
- High throughput requirement for range queries
- Thread safety without synchronization overhead
- Predictable performance (no variance between requests)

## Considered Options

### 1. On-demand Computation (No Cache)

```java
public String convert(int number) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < VALUES.length; i++) {
        while (number >= VALUES[i]) {
            result.append(SYMBOLS[i]);
            number -= VALUES[i];
        }
    }
    return result.toString();
}
```

**Pros:**
- Zero memory overhead
- Simple implementation

**Cons:**
- O(13) operations per request
- Repeated computation for same inputs
- StringBuilder allocation per request

### 2. Lazy Cache (Compute on First Access)

```java
private final ConcurrentHashMap<Integer, String> cache = new ConcurrentHashMap<>();

public String convert(int number) {
    return cache.computeIfAbsent(number, this::computeRomanNumeral);
}
```

**Pros:**
- Only caches what's used
- Thread-safe

**Cons:**
- ConcurrentHashMap overhead
- First request for each number is slow
- Unpredictable latency

### 3. Pre-computed Cache (Chosen)

```java
@PostConstruct
public void initialize() {
    Map<Integer, String> temp = new HashMap<>(3999);
    for (int i = 1; i <= 3999; i++) {
        temp.put(i, computeRomanNumeral(i));
    }
    this.cache = Collections.unmodifiableMap(temp);
}
```

**Pros:**
- O(1) lookup after startup
- Predictable latency
- No synchronization needed (immutable)
- Thread-safe by design

**Cons:**
- ~40KB memory usage
- Slightly longer startup time (~10ms)

## Decision

**Use pre-computed immutable cache (Option 3).**

At startup, compute all 3,999 Roman numeral conversions and store them in an immutable `Map`. This provides:

- **O(1) runtime complexity** for all lookups
- **Thread safety** without locks (immutable after initialization)
- **Predictable performance** with no cold-start issues
- **Minimal memory footprint** (~40KB for 3,999 strings)

## Consequences

### Positive

- **Consistent low latency**: Every request is O(1), no variance
- **No synchronization overhead**: Immutable map is inherently thread-safe
- **Simplified testing**: Cache is populated at startup, easy to verify
- **Range queries benefit**: Parallel processing of range queries gains efficiency

### Negative

- **Startup time**: ~10ms additional startup time
- **Memory usage**: ~40KB fixed memory allocation
- **Inflexibility**: Changing the range requires code changes

### Risks

- **Memory pressure**: Minimal risk—40KB is negligible on modern JVMs
- **Startup failure**: Cache initialization is critical path; covered by health checks

## Complexity Analysis

| Metric | On-demand | Lazy Cache | Pre-computed |
|--------|-----------|------------|--------------|
| Lookup Time | O(13) | O(1)* | O(1) |
| Memory | O(1) | O(n) | O(n) |
| Thread Safety | ✅ | ✅ | ✅ |
| Predictable | ❌ | ❌ | ✅ |

*After warming

## Compliance

- Meets performance requirement (< 10ms p99)
- Meets specification for range 1-3999
- Memory usage is within acceptable limits

## Notes

- Cache size: 3,999 entries × ~12 bytes average = ~48KB
- Initialization time: Measured at 8-12ms on typical hardware
- Reference: [Wikipedia Roman Numerals](https://en.wikipedia.org/wiki/Roman_numerals)

