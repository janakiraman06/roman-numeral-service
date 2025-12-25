# API Security Documentation

This document describes the security architecture and implementation of the Roman Numeral Service API.

## Table of Contents

- [Overview](#overview)
- [Authentication](#authentication)
- [Security Architecture](#security-architecture)
- [API Key Management](#api-key-management)
- [Security Headers](#security-headers)
- [Rate Limiting](#rate-limiting)
- [Environment Configuration](#environment-configuration)
- [Best Practices](#best-practices)

---

## Overview

The Roman Numeral Service implements a layered security model:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client Request                            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: TLS/HTTPS                                              │
│  - Encryption in transit                                         │
│  - Certificate validation                                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 2: API Key Authentication (Order 0)                       │
│  - Validates X-API-Key header                                    │
│  - Rejects unauthorized requests with 401                        │
│  - Adds user context to MDC for logging                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 3: Rate Limiting (Order 1)                                │
│  - Per-IP token bucket algorithm                                 │
│  - Prevents abuse and DoS                                        │
│  - Returns 429 when exceeded                                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 4: Spring Security                                        │
│  - Security headers (CSP, XSS, etc.)                             │
│  - Session management (stateless)                                │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  Layer 5: Input Validation                                       │
│  - Parameter validation                                          │
│  - Type checking                                                 │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Business Logic                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## Authentication

### API Key Format

API keys follow a structured format for security and usability:

```
rns_demo1234_testkeyforlocaldev
│   │        └── Secret portion (never logged)
│   └── Prefix (8 chars, safe to log)
└── Service identifier
```

### Providing API Keys

Clients can authenticate using three methods (in order of preference):

#### 1. X-API-Key Header (Recommended)

```bash
curl -H "X-API-Key: rns_demo1234_testkeyforlocaldev" \
     http://localhost:8080/romannumeral?query=42
```

#### 2. Authorization Bearer Header

```bash
curl -H "Authorization: Bearer rns_demo1234_testkeyforlocaldev" \
     http://localhost:8080/romannumeral?query=42
```

#### 3. Query Parameter (Not Recommended)

```bash
# Avoid in production - key visible in logs and browser history
curl "http://localhost:8080/romannumeral?query=42&api_key=rns_demo1234_testkeyforlocaldev"
```

### Authentication Response Codes

| Status | Meaning | Response |
|--------|---------|----------|
| 200 | Authenticated | Normal response |
| 401 | Missing key | `Error: API key is required...` |
| 401 | Invalid key | `Error: Invalid or expired API key` |

---

## Security Architecture

### Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                           Spring Boot Application                     │
│                                                                       │
│  ┌────────────────────┐    ┌────────────────────┐                    │
│  │ ApiKeyAuthFilter   │───>│   ApiKeyService    │                    │
│  │ (Order: 0)         │    │                    │                    │
│  └────────────────────┘    └─────────┬──────────┘                    │
│           │                          │                                │
│           │                          ▼                                │
│           │                ┌────────────────────┐                    │
│           │                │  ApiKeyRepository  │                    │
│           │                └─────────┬──────────┘                    │
│           │                          │                                │
│           ▼                          ▼                                │
│  ┌────────────────────┐    ┌────────────────────┐                    │
│  │  RateLimitFilter   │    │     PostgreSQL     │                    │
│  │  (Order: 1)        │    │    ┌──────────┐    │                    │
│  └────────────────────┘    │    │ api_keys │    │                    │
│           │                │    │ users    │    │                    │
│           ▼                │    └──────────┘    │                    │
│  ┌────────────────────┐    └────────────────────┘                    │
│  │  SecurityConfig    │                                              │
│  │  (Headers/Session) │                                              │
│  └────────────────────┘                                              │
│           │                                                           │
│           ▼                                                           │
│  ┌────────────────────┐                                              │
│  │    Controller      │                                              │
│  └────────────────────┘                                              │
└──────────────────────────────────────────────────────────────────────┘
```

### Data Flow

```
1. Request arrives with X-API-Key header
           │
           ▼
2. ApiKeyAuthenticationFilter extracts key
           │
           ▼
3. ApiKeyService computes SHA-256 hash
           │
           ▼
4. ApiKeyRepository queries by hash
           │
           ▼
5. Validation checks:
   - Key exists?
   - active = true?
   - revoked_at IS NULL?
   - expires_at > now OR NULL?
           │
           ▼
6. If valid: Update last_used_at, proceed
   If invalid: Return 401 Unauthorized
```

---

## API Key Management

### Database Schema

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) UNIQUE NOT NULL,
    email           VARCHAR(255),
    display_name    VARCHAR(100),
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    active          BOOLEAN DEFAULT TRUE,
    version         BIGINT DEFAULT 0
);

CREATE TABLE api_keys (
    id                  BIGSERIAL PRIMARY KEY,
    key_prefix          VARCHAR(12) NOT NULL,
    key_hash            VARCHAR(64) UNIQUE NOT NULL,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(500),
    user_id             BIGINT REFERENCES users(id),
    created_at          TIMESTAMP NOT NULL,
    expires_at          TIMESTAMP,
    revoked_at          TIMESTAMP,
    last_used_at        TIMESTAMP,
    active              BOOLEAN DEFAULT TRUE,
    rate_limit_override INTEGER
);

CREATE INDEX idx_api_keys_key_hash ON api_keys(key_hash);
CREATE INDEX idx_api_keys_prefix ON api_keys(key_prefix);
CREATE INDEX idx_api_keys_user_id ON api_keys(user_id);
```

### Key Lifecycle

```
   ┌──────────────┐
   │   Generate   │
   │  (API call)  │
   └──────┬───────┘
          │
          ▼
   ┌──────────────┐
   │    Active    │◄─────────────────┐
   │              │                  │
   └──────┬───────┘                  │
          │                          │
          ├─────────────┬────────────┤
          │             │            │
          ▼             ▼            │
   ┌──────────────┐  ┌──────────────┐│
   │   Revoked    │  │   Expired    ││
   │ (immediate)  │  │  (TTL-based) ││
   └──────────────┘  └──────────────┘│
                                     │
                              ┌──────┴───────┐
                              │   Renew/     │
                              │   Rotate     │
                              └──────────────┘
```

### Key Operations

| Operation | Method | Description |
|-----------|--------|-------------|
| Generate | `ApiKeyService.generateApiKey()` | Creates new key for user |
| Validate | `ApiKeyService.validateKey()` | Checks key validity |
| Revoke | `ApiKeyService.revokeKey()` | Immediately disables key |

---

## Security Headers

The application sets these security headers via Spring Security:

| Header | Value | Purpose |
|--------|-------|---------|
| X-Content-Type-Options | nosniff | Prevent MIME sniffing |
| X-Frame-Options | DENY | Prevent clickjacking |
| X-XSS-Protection | 1; mode=block | XSS filter (legacy browsers) |
| Content-Security-Policy | default-src 'self' | Restrict resource loading |
| Referrer-Policy | strict-origin-when-cross-origin | Control referrer info |
| Permissions-Policy | geolocation=(), etc. | Disable unused features |

---

## Rate Limiting

### Algorithm

Uses the **Token Bucket Algorithm** via Bucket4j:

- Each IP gets a bucket with `requests-per-minute` tokens
- Tokens refill continuously
- Request consumes 1 token
- Empty bucket → 429 Too Many Requests

### Response Headers

```
X-Rate-Limit-Limit: 100
X-Rate-Limit-Remaining: 95
Retry-After: 30  (only on 429)
```

---

## Environment Configuration

### Development Profile

```yaml
# application-dev.yml
app:
  api-security:
    enabled: false  # No API key required
  rate-limiting:
    requests-per-minute: 1000
```

### Production Profile

```yaml
# application-prod.yml
app:
  api-security:
    enabled: true   # API key required
  rate-limiting:
    requests-per-minute: 100
```

---

## Best Practices

### For API Consumers

1. **Store keys securely** - Use environment variables or secret managers
2. **Use HTTPS only** - Never send keys over unencrypted connections
3. **Rotate keys regularly** - Generate new keys periodically
4. **Use separate keys** - Different keys for dev/staging/prod
5. **Handle 401 gracefully** - Implement key refresh logic

### For Operators

1. **Monitor validation failures** - Track `api.key.validation.failure` metric
2. **Review unused keys** - Check `last_used_at` for cleanup
3. **Set expiration dates** - Enforce key rotation
4. **Audit key access** - Log prefix (never full key)
5. **Use connection pooling** - Prevent database bottlenecks

---

## Metrics

The following Prometheus metrics are available:

| Metric | Type | Description |
|--------|------|-------------|
| `api_key_validation_success_total` | Counter | Successful validations |
| `api_key_validation_failure_total` | Counter | Failed validations |

---

## Demo Key

For local development and testing, a demo key is automatically created:

```
API Key: rns_demo1234_testkeyforlocaldev
User: demo
```

**⚠️ Do not use in production!**

---

## See Also

- [ADR-010: API Key Authentication](adr/010-api-key-authentication.md)
- [Spring Security Configuration](../src/main/java/com/adobe/romannumeral/config/SecurityConfig.java)
- [API Key Filter](../src/main/java/com/adobe/romannumeral/filter/ApiKeyAuthenticationFilter.java)

