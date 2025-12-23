# Roman Numeral Service

A production-ready REST API for converting integers to Roman numerals, built with Java 21 and Spring Boot for the Adobe AEM Engineering Assessment.

[![CI/CD Pipeline](https://github.com/janakiraman06/roman-numeral-service/actions/workflows/ci.yml/badge.svg)](https://github.com/janakiraman06/roman-numeral-service/actions/workflows/ci.yml)

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

| Category | Tests | Coverage |
|----------|-------|----------|
| Unit Tests (Converter) | 45 | 100% |
| Integration Tests (API) | 22 | All endpoints |
| **Total** | **67+** | Comprehensive |

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

Access Grafana at http://localhost:3000 (admin/admin) to view:

- API response time percentiles (p50, p95, p99)
- Request rate
- Error rate
- JVM heap memory

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

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

---

## License

This project was created for the Adobe AEM Engineering Assessment.

---

## Author

Created by Janakiraman for the Adobe AEM Engineering team assessment.

