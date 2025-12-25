# ADR-009: Offset-Based Pagination for Range Queries

## Status

Accepted

## Date

2025-12-25

## Context

The Roman Numeral Service supports range queries that can potentially return up to 3,999 items (the full range of valid Roman numerals). Without limits, this creates:

1. **Performance issues**: Processing 3,999 items takes noticeable time
2. **Memory pressure**: Large response payloads
3. **Network overhead**: Transferring large JSON responses
4. **Client handling**: Clients may struggle with large datasets

Previously, we limited range size to 1,000 items and rejected larger requests. This is restrictive and not user-friendly.

**Goal**: Allow full range queries (1-3999) while maintaining performance and usability.

## Decision Drivers

- Allow full range queries as per specification
- Maintain responsive API performance
- Provide good developer experience
- Keep implementation simple
- Maintain backward compatibility

## Considered Options

### 1. Cursor-Based Pagination

```
GET /romannumeral?min=1&max=3999&cursor=MTAw
Response: { "conversions": [...], "nextCursor": "MjAw" }
```

**Pros:**
- Consistent results during concurrent modifications
- Good for infinite scroll UIs
- No issues with data changes

**Cons:**
- More complex implementation
- Opaque cursors harder to debug
- Our data is immutable (cursors solve a problem we don't have)
- Can't jump to arbitrary page

### 2. Offset-Based Pagination (Chosen)

```
GET /romannumeral?min=1&max=3999&offset=100&limit=100
Response: { "conversions": [...], "pagination": { "offset": 100, "limit": 100, "totalItems": 3999, ... } }
```

**Pros:**
- Simple to implement and understand
- Clients can jump to any page
- Easy to debug (offset is just a number)
- Works well for our immutable data
- Widely understood pattern

**Cons:**
- Can have issues with concurrent modifications (not a concern for our immutable data)
- Slightly more overhead for deep pages (negligible for our O(1) lookups)

### 3. Keep Size Limit (Status Quo)

```
GET /romannumeral?min=1&max=1000  ✓
GET /romannumeral?min=1&max=3999  ✗ Error: Range too large
```

**Pros:**
- Simple
- No pagination complexity

**Cons:**
- Doesn't allow full range queries
- Unfriendly error for legitimate use cases
- Arbitrary limit feels artificial

## Decision

**Use offset-based pagination (Option 2).**

### Implementation

```
GET /romannumeral?min=1&max=3999                    → First 100 items (default)
GET /romannumeral?min=1&max=3999&limit=500          → First 500 items
GET /romannumeral?min=1&max=3999&offset=100&limit=100 → Items 101-200
```

### Response Format

```json
{
    "conversions": [
        {"input": "101", "output": "CI"},
        {"input": "102", "output": "CII"},
        ...
    ],
    "pagination": {
        "offset": 100,
        "limit": 100,
        "totalItems": 3999,
        "totalPages": 40,
        "currentPage": 2,
        "hasNext": true,
        "hasPrevious": true
    }
}
```

### Parameters

| Parameter | Default | Max | Description |
|-----------|---------|-----|-------------|
| `offset` | 0 | N/A | Starting position (0-based) |
| `limit` | 100 | 500 | Items per page |

### Backward Compatibility

For small ranges (≤500 items), the original response format is used:

```json
{
    "conversions": [
        {"input": "1", "output": "I"},
        {"input": "2", "output": "II"},
        {"input": "3", "output": "III"}
    ]
}
```

For large ranges (>500 items), pagination is automatically enabled.

## Consequences

### Positive

- **Full range support**: Clients can query any valid range (1-3999)
- **Predictable performance**: Each page processed in consistent time
- **Flexible**: Clients control page size (up to 500)
- **Simple**: Offset/limit is widely understood
- **Backward compatible**: Small ranges work as before
- **Discoverability**: Response includes navigation hints

### Negative

- **More complex response**: Pagination metadata adds payload size
- **Multiple requests**: Full range requires multiple API calls
- **Client responsibility**: Clients must handle pagination

### Risks

- **Deep pagination**: Offset 3900 with limit 100 is fine (O(1) lookups)
- **Pagination state**: Stateless server, clients manage their position

## Complexity Analysis

| Metric | Without Pagination | With Pagination |
|--------|-------------------|-----------------|
| Max items per request | 1000 | 500 |
| Full range (3999) | Not allowed | 8 requests |
| Response size | Up to ~100KB | Up to ~15KB |
| Processing time | Variable | Consistent |

## Compliance

- Removes artificial range size limit
- Allows full 1-3999 range as per specification
- Maintains multithreading for each page
- Results still sorted in ascending order

## Notes

- Pagination is optional for small ranges
- `totalItems` in response helps clients plan
- `hasNext`/`hasPrevious` enables simple navigation
- Consider caching headers for paginated responses in future

## References

- [REST API Pagination Best Practices](https://www.moesif.com/blog/technical/api-design/REST-API-Design-Filtering-Sorting-and-Pagination/)
- [ADR-008: Hybrid Threading Strategy](008-hybrid-threading-strategy.md)

