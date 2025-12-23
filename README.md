# Roman Numeral Service

A production-ready REST API for converting integers to Roman numerals, built with Java 21 and Spring Boot for the Adobe AEM Engineering Assessment.

[![CI/CD Pipeline](https://github.com/janakiraman06/roman-numeral-service/actions/workflows/ci.yml/badge.svg)](https://github.com/janakiraman06/roman-numeral-service/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Assessment-blue.svg)]()

---

## Overview

### Problem Statement

Build a web service that converts integers to Roman numerals, supporting:
- Single integer conversion (1-3999)
- Parallel range conversion with multithreading
- Production-grade observability (metrics, logging, monitoring)

### Solution Highlights

| Aspect | Implementation |
|--------|----------------|
| **Performance** | O(1) lookup via pre-computed cache (~40KB memory) |
| **Concurrency** | Java 21 Virtual Threads for lightweight parallelism |
| **Observability** | Prometheus metrics, Grafana dashboards, Loki logs |
| **Reliability** | Rate limiting, correlation IDs, graceful error handling |
| **Quality** | 90%+ test coverage, Checkstyle, CI/CD pipeline |

---

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
- [Architecture](#architecture)
- [Build and Run](#build-and-run)
- [Testing](#testing)
- [Docker](#docker)
- [Observability](#observability)
- [Project Structure](#project-structure)
- [Engineering Methodology](#engineering-methodology)
- [Dependencies](#dependencies)

---

## Features

- **Single Conversion**: Convert integers (1-3999) to Roman numerals
- **Range Conversion**: Parallel processing using Java 21 virtual threads
- **Production Ready**: Metrics, logging, health checks, rate limiting
- **Fully Tested**: Unit tests, integration tests, load tests
- **Containerized**: Docker + Docker Compose with full observability stack
- **CI/CD**: GitHub Actions pipeline with quality gates

---

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.9+ (or use included wrapper)
- Docker & Docker Compose (for full stack)

### Run Locally

```bash
# Clone the repository
git clone https://github.com/janakiraman06/roman-numeral-service.git
cd roman-numeral-service

# Build and run
./mvnw spring-boot:run

# Test the API
curl "http://localhost:8080/romannumeral?query=42"
# Response: {"input":"42","output":"XLII"}
```

### Run with Docker

```bash
# Build and start the full stack
docker-compose up -d

# Access the services
# API:      http://localhost:8080
# Grafana:  http://localhost:3000 (admin/admin)
# Prometheus: http://localhost:9090
```

---

## API Reference

### Single Conversion

Converts a single integer to a Roman numeral.

```
GET /romannumeral?query={integer}
```

**Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| query | integer | Yes | Value between 1 and 3999 |

**Success Response (200 OK):**
```json
{
  "input": "42",
  "output": "XLII"
}
```

**Example:**
```bash
curl "http://localhost:8080/romannumeral?query=1994"
# {"input":"1994","output":"MCMXCIV"}
```

### Range Conversion

Converts a range of integers using parallel processing.

```
GET /romannumeral?min={integer}&max={integer}
```

**Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| min | integer | Yes | Minimum value (1-3999) |
| max | integer | Yes | Maximum value (1-3999), must be > min |

**Success Response (200 OK):**
```json
{
  "conversions": [
    {"input": "1", "output": "I"},
    {"input": "2", "output": "II"},
    {"input": "3", "output": "III"}
  ]
}
```

**Example:**
```bash
curl "http://localhost:8080/romannumeral?min=1&max=5"
```

### Error Responses

Errors are returned in **plain text** format with appropriate HTTP status codes.

| Status | Description | Example |
|--------|-------------|---------|
| 400 | Invalid input | `Error: Number must be between 1 and 3999` |
| 429 | Rate limit exceeded | `Error: Rate limit exceeded. Please wait...` |
| 500 | Server error | `Error: An unexpected error occurred` |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Client Request                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Filter Chain                              │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ CorrelationId    │  │ RateLimitFilter  │                 │
│  │ Filter           │  │ (Bucket4j)       │                 │
│  └──────────────────┘  └──────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                  RomanNumeralController                      │
│              (REST endpoints, validation)                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   RomanNumeralService                        │
│              (Business logic orchestration)                  │
└─────────────────────────────────────────────────────────────┘
                    │                   │
                    ▼                   ▼
┌───────────────────────┐   ┌───────────────────────────────┐
│ RomanNumeralConverter │   │ ParallelRangeProcessor        │
│ (Pre-computed cache)  │   │ (Virtual threads)             │
└───────────────────────┘   └───────────────────────────────┘
```

### Key Design Decisions

1. **Pre-computed Cache**: All 3999 conversions computed at startup for O(1) lookups
2. **Virtual Threads**: Java 21 feature for lightweight parallel processing
3. **Interface-based Design**: Strategy pattern for converter extensibility
4. **Immutable DTOs**: Java records for thread-safe response objects

---

## Build and Run

### Build

```bash
# Compile
./mvnw clean compile

# Package (creates JAR)
./mvnw clean package -DskipTests

# Full build with tests
./mvnw clean verify
```

### Run

```bash
# Using Maven
./mvnw spring-boot:run

# Using JAR
java -jar target/roman-numeral-service-1.0.0.jar

# With custom port
java -jar target/roman-numeral-service-1.0.0.jar --server.port=9000
```

### Configuration

Key configuration in `application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| server.port | 8080 | Application port |
| management.server.port | 8081 | Actuator port |
| app.rate-limiting.requests-per-minute | 100 | Rate limit per IP |

---

## Testing

### Run All Tests

```bash
./mvnw test
```

### Test Coverage

```bash
# Run tests with coverage report
./mvnw clean verify

# View HTML report
open target/site/jacoco/index.html
```

| Category | Description |
|----------|-------------|
| **Unit Tests** | Parameterized tests for all 3999 conversions |
| **Integration Tests** | Full HTTP request/response verification |
| **Coverage Tool** | JaCoCo (report at `target/site/jacoco/`) |

Coverage targets:
- **Line Coverage**: > 80%
- **Branch Coverage**: > 70%
- **Critical Paths**: 100% (converter, controller)

### Test Types

- **Unit Tests**: `StandardRomanNumeralConverterTest` - Parameterized tests for all conversions
- **Integration Tests**: `RomanNumeralIntegrationTest` - Full HTTP request/response verification

### Load Testing

See [load-tests/README.md](load-tests/README.md) for k6 load testing instructions.

```bash
# Quick smoke test (requires k6)
cd load-tests/scripts
k6 run smoke-test.js
```

---

## Docker

### Build Image

```bash
docker build -t roman-numeral-service .
```

### Run Container

```bash
docker run -p 8080:8080 -p 8081:8081 roman-numeral-service
```

### Full Stack (with Observability)

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f roman-numeral-service

# Stop all services
docker-compose down
```

### Services

| Service | Port | URL |
|---------|------|-----|
| Application | 8080 | http://localhost:8080 |
| Actuator | 8081 | http://localhost:8081/actuator |
| Prometheus | 9090 | http://localhost:9090 |
| Grafana | 3000 | http://localhost:3000 |
| Loki | 3100 | http://localhost:3100 |

---

## Observability

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           OBSERVABILITY STACK                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────────────────────┐                                    │
│  │     Roman Numeral Service           │                                    │
│  │     (Spring Boot Application)       │                                    │
│  │                                     │                                    │
│  │  :8080 API ──────────────────────────────────────────► Users/Clients    │
│  │                                     │                                    │
│  │  :8081 Actuator ─────┬──────────────┼───────────────────────────────┐   │
│  │                      │              │                               │   │
│  │  stdout/stderr ──────┼──────────────┼─────────────────────────┐     │   │
│  └──────────────────────┼──────────────┘                         │     │   │
│                         │                                        │     │   │
│         ┌───────────────┘                                        │     │   │
│         │ /actuator/prometheus                                   │     │   │
│         │ (metrics scrape)                                       │     │   │
│         ▼                                                        ▼     │   │
│  ┌─────────────────┐                                  ┌─────────────┐ │   │
│  │   Prometheus    │                                  │  Promtail   │ │   │
│  │   :9090         │                                  │  (agent)    │ │   │
│  │                 │                                  │             │ │   │
│  │  • Scrapes      │                                  │ • Reads     │ │   │
│  │    metrics      │                                  │   Docker    │ │   │
│  │  • Stores       │                                  │   logs      │ │   │
│  │    time-series  │                                  │ • Pushes    │ │   │
│  └────────┬────────┘                                  │   to Loki   │ │   │
│           │                                           └──────┬──────┘ │   │
│           │                                                  │        │   │
│           │ PromQL queries                                   │ push   │   │
│           │                                                  ▼        │   │
│           │                                           ┌─────────────┐ │   │
│           │                                           │    Loki     │ │   │
│           │                                           │    :3100    │ │   │
│           │                                           │             │ │   │
│           │                                           │ • Indexes   │ │   │
│           │                                           │   logs      │ │   │
│           │                                           │ • Stores    │ │   │
│           │                                           │   with      │ │   │
│           │                                           │   labels    │ │   │
│           │                                           └──────┬──────┘ │   │
│           │                                                  │        │   │
│           │                              LogQL queries       │        │   │
│           │                                                  │        │   │
│           └──────────────────┐      ┌────────────────────────┘        │   │
│                              │      │                                 │   │
│                              ▼      ▼                                 │   │
│                        ┌─────────────────┐          /actuator/health  │   │
│                        │    Grafana      │◄───────────────────────────┘   │
│                        │    :3000        │                                │
│                        │                 │                                │
│                        │ • Dashboards    │                                │
│                        │ • Alerts        │                                │
│                        │ • Visualization │                                │
│                        └─────────────────┘                                │
│                                                                           │
└───────────────────────────────────────────────────────────────────────────┘
```

### Data Flow Summary

| Flow | Path | Port | Protocol |
|------|------|------|----------|
| **Metrics** | App → Prometheus → Grafana | 8081 → 9090 → 3000 | HTTP (pull) |
| **Logs** | App → Docker → Promtail → Loki → Grafana | stdout → file → 3100 → 3000 | HTTP (push) |
| **Health** | Grafana → App (Actuator) | 3000 → 8081 | HTTP (pull) |

### Metrics

Available at `/actuator/prometheus` (port 8081):

- `http_server_requests_seconds` - Request latency histograms
- `jvm_memory_used_bytes` - Memory usage
- `jvm_threads_live_threads` - Thread count

### Health Checks

```bash
# Liveness
curl http://localhost:8081/actuator/health/liveness

# Readiness
curl http://localhost:8081/actuator/health/readiness
```

### Grafana Dashboard

**Direct Dashboard URL:** http://localhost:3000/d/roman-numeral-service

Login: `admin` / `admin`

The pre-configured production dashboard includes **15 panels** across 4 rows:

| Row | Panels |
|-----|--------|
| **🔴 Critical Metrics (SLA)** | Availability SLA %, Response Time (p99), Error Rate (5xx), Request Rate |
| **📊 Request Metrics** | Response Time Percentiles, Requests by Status Code, Status Distribution Pie, 4xx Rate, Total Requests |
| **💻 JVM Metrics** | Heap Memory, CPU Usage, Thread Count, GC Pause Time, Non-Heap Memory |
| **📋 Logs** | Live application logs from Loki |

**Production-grade defaults:** Panels show "No traffic" instead of misleading values (like 0% error rate) when there are no requests to measure.

**Debugging 5xx errors:**
1. Check the **Error Rate (5xx)** panel for the rate
2. Query Prometheus: `sum(increase(http_server_requests_seconds_count{status=~"5.."}[15m])) by (status, uri, exception)`
3. Check **Application Logs** panel in Loki for stack traces

### Logging

Logs include correlation IDs for request tracing:

```
2024-01-15 10:30:00 [main] [abc12345] INFO Controller - Processing request
```

---

## Project Structure

```
roman-numeral-service/
├── src/
│   ├── main/
│   │   ├── java/com/adobe/romannumeral/
│   │   │   ├── RomanNumeralApplication.java    # Entry point
│   │   │   ├── controller/                      # REST endpoints
│   │   │   ├── service/                         # Business logic
│   │   │   ├── converter/                       # Conversion algorithm
│   │   │   ├── model/                           # DTOs (records)
│   │   │   ├── exception/                       # Error handling
│   │   │   ├── config/                          # Configuration
│   │   │   └── filter/                          # HTTP filters
│   │   └── resources/
│   │       ├── application.yml                  # Configuration
│   │       └── logback-spring.xml               # Logging config
│   └── test/                                    # Test classes
├── docker/                                      # Docker configs
│   ├── prometheus/
│   ├── loki/
│   └── grafana/
├── load-tests/                                  # k6 load tests
├── .github/workflows/                           # CI/CD pipeline
├── Dockerfile                                   # Multi-stage build
├── docker-compose.yml                           # Full stack
├── pom.xml                                      # Maven config
├── TECH_DECISIONS.md                            # Technology choices
└── README.md                                    # This file
```

---

## Engineering Methodology

### Development Approach

1. **Test-Driven Development**: Tests written alongside implementation
2. **Incremental Commits**: Each commit represents a complete, working unit
3. **SOLID Principles**: Interface-based design, single responsibility
4. **Clean Code**: Meaningful names, small methods, comprehensive JavaDoc

### Roman Numeral Algorithm

Based on the [Wikipedia Roman Numerals specification](https://en.wikipedia.org/wiki/Roman_numerals).

**Algorithm**: Greedy subtraction with value-symbol mapping

```java
// Values in descending order (includes subtractive combinations)
int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
```

**Complexity**:
- Time: O(1) - Pre-computed cache lookup
- Space: O(n) - Cache for n=3999 entries (~40KB)

### Quality Gates

- **Checkstyle**: Google Java Style Guide
- **JaCoCo**: Code coverage reporting  
- **Integration Tests**: Full API verification
- **Load Tests**: Performance validation

### Design Patterns Used

| Pattern | Where | Why |
|---------|-------|-----|
| **Strategy** | `RomanNumeralConverter` interface | Extensibility for different conversion algorithms |
| **Singleton** | Pre-computed cache | Single source of truth, thread-safe |
| **Filter Chain** | `CorrelationIdFilter`, `RateLimitFilter` | Cross-cutting concerns separation |
| **Builder** | Response DTOs (Records) | Immutable objects |
| **Dependency Injection** | All components | Testability, loose coupling |

---

## Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Port 8080 in use | Another service running | `lsof -i :8080` and kill the process |
| Docker build fails | Missing dependencies | `docker system prune -a` and rebuild |
| Swagger UI 403 | Security filter blocking | Use `/swagger-ui/index.html` not `/swagger-ui.html` |
| Grafana "No data" | Datasource UID mismatch | Check datasource provisioning has correct UIDs |
| Loki no logs (macOS) | Container path issue | Using Loki Docker driver (already configured) |

### Health Check Commands

```bash
# Application health
curl http://localhost:8081/actuator/health

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus | head -20

# Test API
curl "http://localhost:8080/romannumeral?query=42"
```

---

## Dependencies

### Runtime Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot Starter Web | 3.4.1 | REST API framework |
| Spring Boot Starter Actuator | 3.4.1 | Production monitoring |
| Spring Boot Starter Security | 3.4.1 | Security headers |
| Spring Boot Starter Validation | 3.4.1 | Input validation |
| Micrometer Prometheus | 1.12.x | Metrics export |
| Bucket4j Core | 8.10.1 | Rate limiting |
| SpringDoc OpenAPI | 2.7.0 | API documentation |

### Test Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot Starter Test | 3.4.1 | Testing framework |
| JUnit 5 | 5.10.x | Unit testing |
| MockMvc | - | Integration testing |

### Build Plugins

| Plugin | Purpose |
|--------|---------|
| Spring Boot Maven Plugin | Executable JAR packaging |
| Maven Compiler Plugin | Java 21 compilation |
| JaCoCo Maven Plugin | Code coverage |
| Checkstyle Maven Plugin | Code style enforcement |

---

## API Documentation

Interactive API documentation available at:

- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

---

## Design Decisions & Trade-offs

### Why Pre-computed Cache vs On-demand Computation?

| Approach | Pros | Cons |
|----------|------|------|
| **Pre-computed (Chosen)** | O(1) lookup, zero latency, thread-safe | ~40KB memory, startup time |
| **On-demand** | Zero memory overhead | O(13) per request, repeated computation |

**Decision**: Pre-compute. For a high-throughput API, trading 40KB memory for O(1) lookups is optimal.

### Why Virtual Threads vs Thread Pool?

| Approach | Pros | Cons |
|----------|------|------|
| **Virtual Threads (Chosen)** | Lightweight, no pool tuning, scales naturally | Java 21+ only |
| **Fixed Thread Pool** | Predictable resource usage | Pool size tuning, overhead |

**Decision**: Virtual threads. For I/O-bound range queries, virtual threads provide better scalability with simpler code.

### Why Plain Text Errors vs JSON?

The API specification states errors "can be plain text". In production, RFC 7807 Problem Details (JSON) is preferred, but for this assessment we follow the spec exactly.

For more details, see [TECH_DECISIONS.md](TECH_DECISIONS.md).

---

## Non-Functional Requirements

| Requirement | Implementation | Target |
|-------------|----------------|--------|
| **Latency** | Pre-computed cache | < 10ms p99 |
| **Throughput** | Virtual threads + rate limiting | 100 req/min per IP |
| **Availability** | Health checks, graceful shutdown | 99.9% |
| **Observability** | Prometheus, Grafana, Loki | Full stack |
| **Security** | CORS, CSP, rate limiting | Defense in depth |

---

## Security Considerations

- **Rate Limiting**: 100 requests/minute per IP (Bucket4j)
- **Security Headers**: CSP, X-Frame-Options, X-Content-Type-Options
- **No Authentication**: Not required per spec (would add OAuth2/JWT for production)
- **Input Validation**: Strict range checking (1-3999)
- **Correlation IDs**: Request tracing without exposing internals

---

## Performance Benchmarks

Tested with k6 load testing tool:

| Scenario | VUs | Duration | p95 Latency | Throughput |
|----------|-----|----------|-------------|------------|
| Smoke | 1 | 30s | < 50ms | ~30 req/s |
| Load | 50 | 5m | < 100ms | ~500 req/s |
| Stress | 200 | 10m | < 500ms | ~1000 req/s |

---

## Known Limitations & Future Enhancements

### Current Limitations

1. **Range**: 1-3999 only (standard Roman numeral range)
2. **Authentication**: Not implemented (not required by spec)
3. **Distributed Cache**: In-memory only (no Redis)

### Potential Enhancements

1. **Extended Range**: Support larger numbers with vinculum notation
2. **Reverse Conversion**: Roman numeral to integer
3. **Kubernetes**: Helm charts for K8s deployment
4. **Distributed Caching**: Redis for horizontal scaling

### API Versioning Strategy (If Needed)

For this assessment, versioning is **intentionally not implemented** because:
- The spec defines exact URLs without version prefixes
- No breaking changes are anticipated
- Over-engineering is a liability, not an asset

**If versioning were required**, we would use **header-based versioning**:
```
GET /romannumeral?query=42
Accept-Version: v1
```

This approach:
- Preserves clean URLs matching the spec
- Allows version negotiation without URL changes
- Is documented in the controller code for future reference

---

## License

This project was created for the Adobe AEM Engineering Assessment.

---

## Author

Created by Janakiraman for the Adobe AEM Engineering team assessment.

