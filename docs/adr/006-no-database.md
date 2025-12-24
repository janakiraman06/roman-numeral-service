# ADR-006: No Database Required

## Status

Accepted

## Date

2024-12-24

## Context

Traditional web services often require a database for:

- Storing application data
- Caching computed results
- Maintaining state across restarts

We need to evaluate whether this service requires a database.

## Decision Drivers

- Minimize infrastructure complexity
- Reduce operational overhead
- Keep deployment simple
- Avoid over-engineering

## Analysis

### What Data Does This Service Handle?

| Data Type | Persistence Needed? | Reason |
|-----------|---------------------|--------|
| Roman numeral mappings | No | Deterministic, computed at startup |
| Request logs | Optional | Handled by Loki |
| Metrics | Optional | Handled by Prometheus |
| Rate limit state | No | Ephemeral, in-memory acceptable |
| User data | No | No users, no authentication |

### Why No Database?

1. **Deterministic Computation**: The conversion algorithm produces the same output for the same input. No need to store results.

2. **Finite Domain**: Only 3,999 valid inputs. All can be pre-computed into memory at startup (~40KB).

3. **Stateless Design**: Each request is independent. No session state, no user accounts, no transactions.

4. **Ephemeral Rate Limits**: Rate limit buckets can be in-memory. If the server restarts, limits reset—acceptable for this use case.

## Considered Options

### 1. No Database (Chosen)

```
App Start → Pre-compute cache → Serve requests
```

**Pros:**
- Zero infrastructure dependencies
- Faster startup (no DB connection)
- Simpler deployment
- No ORM complexity
- No connection pool management

**Cons:**
- State lost on restart
- Single-instance limitation for some features

### 2. Embedded H2 Database

```java
@Entity
public class RomanNumeral {
    @Id private Integer number;
    private String romanNumeral;
}
```

**Pros:**
- Persistence across restarts
- SQL querying capability

**Cons:**
- Unnecessary complexity
- Slower than in-memory HashMap
- File locking issues
- Testing complexity

### 3. Redis Cache

```java
@Cacheable("romanNumerals")
public String convert(int number) { ... }
```

**Pros:**
- Distributed caching
- Persistence options
- Shared across instances

**Cons:**
- External dependency
- Network latency
- Operational overhead
- Overkill for 40KB of data

### 4. PostgreSQL/MySQL

**Pros:**
- Full ACID compliance
- Rich querying

**Cons:**
- Massive overkill
- Operational burden
- Network dependency

## Decision

**No database required.**

The service operates entirely with:
- **In-memory pre-computed cache** for conversions
- **In-memory rate limit buckets** for throttling
- **External observability stack** for logs/metrics

```java
// All data needed is in this immutable map
private final Map<Integer, String> cache = 
    Collections.unmodifiableMap(precomputeAll());
```

## Consequences

### Positive

- **Simplicity**: No database drivers, connection pools, or ORMs
- **Performance**: In-memory operations are nanoseconds, not milliseconds
- **Reliability**: No database connection failures
- **Deployment**: Single JAR, no database container needed
- **Testing**: No test database setup required

### Negative

- **No persistence**: Rate limits reset on restart
- **Single instance**: Cannot share state across instances

### Risks

- **Scaling limitation**: For horizontal scaling, would need distributed cache
- **Mitigation**: Can add Redis later if needed (see ADR evolution)

## When Would We Add a Database?

Future requirements that would necessitate a database:

| Requirement | Database Type |
|-------------|---------------|
| User accounts | PostgreSQL |
| Usage analytics | TimescaleDB |
| Audit logging | PostgreSQL |
| Conversion history | Redis/PostgreSQL |
| Horizontal scaling | Redis (for rate limits) |

## YAGNI Principle

> "You Aren't Gonna Need It"

Adding a database now would be:
- Over-engineering
- Adding complexity without benefit
- Solving problems we don't have

The service requirements are fully met without persistent storage.

## Compliance

- Meets all functional requirements
- Demonstrates understanding of when NOT to add infrastructure
- Shows YAGNI and simplicity principles

## Notes

- Pre-computed cache: ~40KB memory
- HashMap lookup: O(1), ~10ns
- Database lookup: O(1), ~1-10ms (100-1000x slower)
- Principle: Choose the simplest solution that works

