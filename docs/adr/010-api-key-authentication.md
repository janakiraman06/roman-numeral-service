# ADR-010: API Key Authentication

## Status
Accepted

## Date
2024-12-25

## Context

The Roman Numeral Service needs secure API authentication to:
1. **Identify consumers** - Track which applications/users are calling the API
2. **Enable rate limiting per client** - Fair usage enforcement per API key
3. **Support revocation** - Disable access for compromised or decommissioned clients
4. **Enable analytics** - Track usage patterns per consumer
5. **Meet production requirements** - Enterprise APIs require authentication

### Decision Drivers

- **Simplicity**: Easy for API consumers to implement
- **Security**: Protect against unauthorized access and abuse
- **Stateless**: Compatible with horizontal scaling
- **Auditability**: Track all API access for compliance
- **Performance**: Minimal latency overhead per request

## Considered Options

### Option 1: OAuth 2.0 / JWT
- **Pros**: Industry standard, supports refresh tokens, fine-grained scopes
- **Cons**: Complex implementation, requires token refresh logic, overkill for single-purpose API

### Option 2: API Key Authentication
- **Pros**: Simple, stateless, easy client integration, database-backed revocation
- **Cons**: Key exposure risk, no built-in expiration (we add it)

### Option 3: Basic Authentication
- **Pros**: Very simple
- **Cons**: Credentials sent every request (even if over HTTPS), no revocation

### Option 4: Mutual TLS (mTLS)
- **Pros**: Very secure, client certificates
- **Cons**: Complex certificate management, not suitable for public APIs

## Decision

**Chosen: API Key Authentication** with database-backed validation.

For a focused conversion API, API keys provide the right balance of security and simplicity. We enhance basic API key authentication with:

1. **SHA-256 Hashing**: Keys stored as hashes, never plaintext
2. **Key Prefixes**: `rns_` prefix for identification in logs without exposing the secret
3. **Expiration Support**: Optional TTL for automatic key rotation
4. **Revocation**: Immediate revocation via database flag
5. **Usage Tracking**: Last-used timestamp for audit and cleanup

## Implementation Details

### Key Format
```
rns_{prefix}_{secret}
│   │       └── Base64 URL-safe secret (24 bytes)
│   └── 8-char hex identifier for logs/UI
└── Service prefix (roman numeral service)
```

### Authentication Flow
```
┌─────────┐          ┌───────────────┐          ┌──────────────┐
│  Client │──────────│ API Gateway   │──────────│   Database   │
└────┬────┘          └───────┬───────┘          └──────┬───────┘
     │ X-API-Key: rns_xxx    │                         │
     │──────────────────────>│                         │
     │                       │ SHA-256(key)            │
     │                       │────────────────────────>│
     │                       │    SELECT ... WHERE     │
     │                       │    key_hash = ?         │
     │                       │<────────────────────────│
     │                       │ Validate:               │
     │                       │ - active = true         │
     │                       │ - expires_at > now      │
     │                       │ - revoked_at IS NULL    │
     │  200 OK / 401 Unauth  │                         │
     │<──────────────────────│                         │
```

### Key Storage Schema
```sql
CREATE TABLE api_keys (
    id              BIGSERIAL PRIMARY KEY,
    key_prefix      VARCHAR(12) NOT NULL,        -- "rns_abc12345"
    key_hash        VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 hex
    name            VARCHAR(100) NOT NULL,
    user_id         BIGINT REFERENCES users(id),
    created_at      TIMESTAMP NOT NULL,
    expires_at      TIMESTAMP,
    revoked_at      TIMESTAMP,
    last_used_at    TIMESTAMP,
    active          BOOLEAN DEFAULT TRUE,
    rate_limit_override INTEGER
);
```

### Security Measures

| Measure | Implementation |
|---------|----------------|
| No plaintext storage | SHA-256 hash only |
| Timing attack prevention | Constant-time hash comparison |
| Key rotation | Expiration dates + multiple keys per user |
| Audit trail | last_used_at tracking |
| Revocation | Immediate via active flag |
| Key identification | Prefix visible in logs, secret never logged |

### Request Headers

Clients can provide the API key via:
1. **X-API-Key header** (preferred): `X-API-Key: rns_abc12345_secretkey`
2. **Authorization header**: `Authorization: Bearer rns_abc12345_secretkey`
3. **Query parameter** (not recommended): `?api_key=rns_abc12345_secretkey`

### Environment Configuration

```yaml
# application-dev.yml
app:
  api-security:
    enabled: false  # Disabled for easy local development

# application-prod.yml
app:
  api-security:
    enabled: true   # Required in production
```

## Consequences

### Positive
- Simple integration for API consumers
- Database-backed revocation is immediate
- No token refresh complexity
- Stateless validation (cacheable)
- Clear audit trail
- Per-key rate limit overrides possible

### Negative
- Keys must be stored securely by clients
- No automatic expiration enforcement (clients must handle 401)
- Database lookup per request (mitigated by caching)

### Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Key exposure | Use HTTPS only, never log full key |
| Database bottleneck | Connection pooling, consider Redis cache |
| Brute force | Rate limiting by IP before auth |
| Key reuse after revocation | Immediate database check |

## Performance Considerations

- **Latency**: ~1-2ms for database lookup (indexed hash column)
- **Throughput**: HikariCP connection pooling handles concurrent requests
- **Future optimization**: Add Redis/Caffeine cache for validated keys (TTL: 60s)

## Alternatives for Future

If requirements change, we can migrate to:
- **OAuth 2.0 + JWT**: For multi-service authentication
- **API Gateway Integration**: AWS API Gateway, Kong, etc.
- **OIDC**: For user-facing applications

## References

- [OWASP API Security](https://owasp.org/www-project-api-security/)
- [Stripe API Authentication](https://stripe.com/docs/api/authentication)
- [GitHub API Keys](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token)

