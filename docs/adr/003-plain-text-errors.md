# ADR-003: Use Plain Text Error Responses

## Status

Accepted

## Date

2024-12-24

## Context

The API needs to return error responses for various failure scenarios:

- Invalid input (non-integer, out of range)
- Missing parameters
- Rate limit exceeded
- Internal server errors

The specification states:

> "Errors can be returned in plain text format, but success responses must include a JSON payload..."

We need to decide on the error response format.

## Decision Drivers

- Specification compliance
- Simplicity over complexity
- Consistency across error types
- Debuggability for clients

## Considered Options

### 1. Plain Text Errors (Chosen)

```
HTTP/1.1 400 Bad Request
Content-Type: text/plain

Error: Number must be between 1 and 3999, got: 4000
```

**Pros:**
- Explicitly allowed by specification
- Simple to implement
- Human-readable
- No parsing required for debugging

**Cons:**
- Less structured than JSON
- Harder to parse programmatically
- No standard schema

### 2. RFC 7807 Problem Details (JSON)

```json
{
  "type": "https://api.example.com/errors/invalid-input",
  "title": "Invalid Input",
  "status": 400,
  "detail": "Number must be between 1 and 3999, got: 4000",
  "instance": "/romannumeral?query=4000"
}
```

**Pros:**
- Industry standard (RFC 7807)
- Structured and parseable
- Rich metadata
- Better for API clients

**Cons:**
- More complex implementation
- Not required by specification
- Adds overhead

### 3. Custom JSON Error Format

```json
{
  "error": true,
  "message": "Number must be between 1 and 3999, got: 4000",
  "code": "INVALID_INPUT"
}
```

**Pros:**
- Parseable
- Flexible

**Cons:**
- Non-standard
- Extra work without standard benefits

## Decision

**Use plain text error responses (Option 1).**

The specification explicitly permits plain text errors, and we follow the principle of **doing exactly what's required**—no more, no less.

```java
@ExceptionHandler(InvalidInputException.class)
public ResponseEntity<String> handleInvalidInput(InvalidInputException ex) {
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.TEXT_PLAIN)
        .body("Error: " + ex.getMessage());
}
```

## Consequences

### Positive

- **Specification compliance**: Explicitly follows the spec
- **Simplicity**: No complex error DTOs or serialization
- **Human-readable**: Easy to debug with curl
- **Consistent**: Same format for all error types

### Negative

- **Not structured**: Clients must parse text to extract details
- **No error codes**: Less programmatic error handling
- **Production limitation**: Would need enhancement for production API

### Risks

- **API client friction**: Programmatic clients prefer structured errors

## Error Response Patterns

| Scenario | Status | Example Message |
|----------|--------|-----------------|
| Invalid range | 400 | `Error: Number must be between 1 and 3999, got: 4000` |
| Missing param | 400 | `Error: Missing required parameter 'query'` |
| Type mismatch | 400 | `Error: Invalid value 'abc' for parameter 'query'` |
| Rate limited | 429 | `Error: Too many requests. Please try again later.` |
| Server error | 500 | `Error: An unexpected error occurred. (Reference: abc123)` |

## Production Recommendation

For a production API, we would implement RFC 7807:

```java
@ExceptionHandler(InvalidInputException.class)
public ResponseEntity<ProblemDetail> handleInvalidInput(InvalidInputException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setType(URI.create("/errors/invalid-input"));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
}
```

Spring 6+ has built-in `ProblemDetail` support for RFC 7807.

## Compliance

- Matches specification: "Errors can be returned in plain text format"
- Demonstrates understanding of when to follow vs. exceed requirements

## Notes

- RFC 7807: Problem Details for HTTP APIs
- Spring 6+ ProblemDetail class for RFC 7807 support
- Content-Type: text/plain;charset=UTF-8

