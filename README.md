# Roman Numeral Service

A production-ready REST API for converting integers to Roman numerals, built with Java 21 and Spring Boot for the Adobe AEM Engineering Assessment. Includes a comprehensive data platform with streaming ingestion, batch ETL, and BI analytics.

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
| **Performance** | O(1) lookup via pre-computed array cache (~40KB memory) |
| **Concurrency** | Hybrid threading: Spring Virtual Threads + Parallel Streams |
| **Pagination** | Offset-based pagination for range queries (max 500 per page) |
| **Security** | Database-backed API Key authentication (BCrypt hashed) |
| **Events** | Kafka event streaming for analytics pipeline |
| **Data Platform** | Flink + Spark + Iceberg lakehouse with Medallion architecture |
| **Observability** | Prometheus metrics, Grafana dashboards, Loki logs |
| **Quality** | 158 tests, 96%+ coverage, CI/CD pipeline |

---

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
- [Architecture](#architecture)
- [Data Platform](#data-platform)
- [Build and Run](#build-and-run)
- [Testing](#testing)
- [Docker](#docker)
- [Observability](#observability)
- [Security](#security)
- [Architecture Decision Records](#architecture-decision-records)
- [Dependencies](#dependencies)

---

## Features

### Core API
- **Single Conversion**: Convert integers (1-3999) to Roman numerals
- **Range Conversion**: Parallel processing with smart pagination
- **Production Ready**: Metrics, logging, health checks, rate limiting

### Security
- **API Key Authentication**: Database-backed with BCrypt hashing
- **Rate Limiting**: Configurable per-IP limits (Bucket4j)
- **Spring Profiles**: Separate dev/prod configurations

### Data Platform
- **Event Streaming**: Kafka producer for all conversion events
- **Lakehouse**: Iceberg tables on MinIO (S3-compatible)
- **ETL Pipelines**: Airflow-orchestrated Flink + Spark jobs
- **Data Quality**: Great Expectations validation
- **Data Lineage**: Marquez + OpenLineage
- **BI Dashboards**: Apache Superset

### DevOps
- **Containerized**: Docker Compose with 15+ services
- **CI/CD**: GitHub Actions with quality gates
- **Observability**: Full PLG stack (Prometheus, Loki, Grafana)

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
# Build and start the full stack (API + Observability)
docker-compose up -d

# Access the services
# API:        http://localhost:8080
# Grafana:    http://localhost:3000 (admin/admin)
# Prometheus: http://localhost:9090
# Swagger:    http://localhost:8080/swagger-ui/index.html
```

### Run Full Data Platform

```bash
# Start everything including data platform
docker-compose --profile data-platform up -d

# Additional services:
# Airflow:    http://localhost:8280 (airflow/airflow)
# Superset:   http://localhost:8088 (admin/admin)
# Spark UI:   http://localhost:8180
# Flink UI:   http://localhost:8181
# MinIO:      http://localhost:9001 (minioadmin/minioadmin123)
# Marquez:    http://localhost:3001
# Jupyter:    http://localhost:8888 (token: jupyter)
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

### Range Conversion (Paginated)

Converts a range of integers with pagination support.

```
GET /romannumeral?min={integer}&max={integer}&offset={offset}&limit={limit}
```

**Parameters:**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| min | integer | Yes | - | Minimum value (1-3999) |
| max | integer | Yes | - | Maximum value (1-3999) |
| offset | integer | No | 0 | Starting position |
| limit | integer | No | 100 | Results per page (max 500) |

**Success Response (200 OK) - Small Range (≤500):**
```json
{
  "conversions": [
    {"input": "1", "output": "I"},
    {"input": "2", "output": "II"}
  ]
}
```

**Success Response (200 OK) - Large Range (>500):**
```json
{
  "conversions": [...],
  "pagination": {
    "totalItems": 3999,
    "totalPages": 40,
    "currentPage": 1,
    "pageSize": 100,
    "nextOffset": 100,
    "prevOffset": null
  }
}
```

### Authentication

When API security is enabled (`prod` profile), include an API key:

```bash
# Header authentication
curl -H "X-API-Key: your-api-key" "http://localhost:8080/romannumeral?query=42"

# Bearer token
curl -H "Authorization: Bearer your-api-key" "http://localhost:8080/romannumeral?query=42"

# Query parameter
curl "http://localhost:8080/romannumeral?query=42&apiKey=your-api-key"
```

**Dev profile**: API keys are disabled for local development.

### Error Responses

| Status | Description | Example |
|--------|-------------|---------|
| 400 | Invalid input | `Error: Number must be between 1 and 3999` |
| 401 | Missing/invalid API key | `Error: Invalid API Key.` |
| 429 | Rate limit exceeded | `Error: Rate limit exceeded...` |
| 500 | Server error | `Error: An unexpected error occurred` |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CLIENT REQUEST                                   │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           FILTER CHAIN                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                      │
│  │ Correlation │  │   API Key   │  │ Rate Limit  │                      │
│  │ ID Filter   │  │   Filter    │  │   Filter    │                      │
│  └─────────────┘  └─────────────┘  └─────────────┘                      │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      RomanNumeralController                              │
│                   (REST endpoints, validation)                           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
┌─────────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ RomanNumeralService │ │ KafkaProducer   │ │ PostgreSQL      │
│ (Business Logic)    │ │ Service         │ │ (Users/Keys)    │
└─────────────────────┘ └─────────────────┘ └─────────────────┘
          │                     │
          ▼                     ▼
┌─────────────────────┐ ┌─────────────────────────────────────┐
│ Array Cache (O(1))  │ │           KAFKA                     │
│ Parallel Streams    │ │    (roman-numeral-events)           │
└─────────────────────┘ └─────────────────────────────────────┘
                                    │
                                    ▼
                        ┌───────────────────────┐
                        │    DATA PLATFORM      │
                        │  (Flink → Iceberg →   │
                        │   Spark → Superset)   │
                        └───────────────────────┘
```

### Key Design Decisions

| Decision | Rationale | ADR |
|----------|-----------|-----|
| Array Cache | O(1) lookups, 6x faster than HashMap | [ADR-007](docs/adr/007-array-cache-optimization.md) |
| Hybrid Threading | VT for HTTP, Parallel Streams for CPU | [ADR-008](docs/adr/008-hybrid-threading-strategy.md) |
| Offset Pagination | Simple, backward-compatible | [ADR-009](docs/adr/009-pagination.md) |
| API Key Auth | Database-backed, BCrypt hashed | [ADR-010](docs/adr/010-api-key-authentication.md) |
| Kafka Events | Decoupled analytics pipeline | [ADR-011](docs/adr/011-event-driven-architecture.md) |

---

## Data Platform

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    UNIFIED LAKEHOUSE ARCHITECTURE                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌─────────┐     ┌─────────┐     ┌─────────────┐                       │
│   │  API    │────▶│  Kafka  │────▶│    Flink    │                       │
│   │ Events  │     │         │     │  (Stream)   │                       │
│   └─────────┘     └─────────┘     └──────┬──────┘                       │
│                                          │                               │
│                                          ▼                               │
│   ┌──────────────────────────────────────────────────────────────┐      │
│   │                    ICEBERG LAKEHOUSE                          │      │
│   │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐           │      │
│   │  │   BRONZE    │  │   SILVER    │  │    GOLD     │           │      │
│   │  │  (Raw Data) │─▶│  (Curated)  │─▶│ (Analytics) │           │      │
│   │  └─────────────┘  └─────────────┘  └─────────────┘           │      │
│   │        ▲                 ▲                 │                  │      │
│   │        │                 │                 ▼                  │      │
│   │   ┌────┴────┐      ┌────┴────┐     ┌─────────────┐           │      │
│   │   │  Flink  │      │  Spark  │     │  Superset   │           │      │
│   │   │(Ingest) │      │  (ETL)  │     │   (BI)      │           │      │
│   │   └─────────┘      └─────────┘     └─────────────┘           │      │
│   └──────────────────────────────────────────────────────────────┘      │
│                                                                          │
│   Orchestration: Airflow    Quality: Great Expectations                  │
│   Lineage: Marquez          Storage: MinIO (S3)                          │
│   Catalog: Hive Metastore   Analysis: Jupyter                            │
│                                                                          │
└──────────────────────────────────────────────────────────────────────────┘
```

### Medallion Architecture

| Layer | Purpose | Technology | Update Frequency |
|-------|---------|------------|------------------|
| **Bronze** | Raw data preservation | Flink streaming | Real-time |
| **Silver** | Cleaned, deduplicated | Spark batch | Hourly |
| **Gold** | Aggregated metrics | Spark batch | Daily |

### Key Features

- **SCD Type 2**: Historical tracking for `dim_users`
- **Star Schema**: Optimized for analytics queries
- **Data Quality**: Great Expectations validation
- **Data Lineage**: OpenLineage → Marquez
- **Idempotent ETL**: Safe to re-run

See [data-platform/README.md](data-platform/README.md) for detailed documentation.

---

## Build and Run

### Build

```bash
./mvnw clean compile          # Compile
./mvnw clean package -DskipTests  # Package
./mvnw clean verify           # Full build with tests
```

### Run

```bash
# Development profile (no API key required)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Production profile (API key required)
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### Spring Profiles

| Profile | API Keys | Logging | Rate Limit | Database |
|---------|----------|---------|------------|----------|
| `dev` | Disabled | DEBUG | 1000/min | PostgreSQL |
| `prod` | Enabled | INFO | 100/min | PostgreSQL |
| `test` | Disabled | WARN | - | H2 |

---

## Testing

### Run All Tests

```bash
./mvnw test          # Run tests only
./mvnw clean verify  # Run tests + coverage check
```

### Test Summary

| Metric | Current | Threshold |
|--------|---------|-----------|
| **Total Tests** | 158 | - |
| **Line Coverage** | 96%+ | ≥ 75% |
| **Branch Coverage** | 87%+ | ≥ 65% |

### Test Categories

| Category | Description |
|----------|-------------|
| Unit Tests | Isolated component testing |
| Integration Tests | Full HTTP request/response |
| Entity Tests | JPA entity validation |
| Service Tests | Business logic verification |

---

## Docker

### Services Overview

| Service | Port | URL | Credentials |
|---------|------|-----|-------------|
| **API** | 8080 | http://localhost:8080 | - |
| **Actuator** | 8081 | http://localhost:8081/actuator | - |
| **Swagger** | 8080 | http://localhost:8080/swagger-ui/index.html | - |
| **Grafana** | 3000 | http://localhost:3000 | admin/admin |
| **Prometheus** | 9090 | http://localhost:9090 | - |
| **PostgreSQL** | 5432 | - | romannumeral/romannumeral_secret |
| **Kafka** | 9092 | - | - |
| **Airflow** | 8280 | http://localhost:8280 | airflow/airflow |
| **Superset** | 8088 | http://localhost:8088 | admin/admin |
| **Spark UI** | 8180 | http://localhost:8180 | - |
| **Flink UI** | 8181 | http://localhost:8181 | - |
| **MinIO** | 9001 | http://localhost:9001 | minioadmin/minioadmin123 |
| **Marquez** | 3001 | http://localhost:3001 | - |
| **Jupyter** | 8888 | http://localhost:8888 | token: jupyter |

### Quick Commands

```bash
# Start core services
docker-compose up -d

# Start with data platform
docker-compose --profile data-platform up -d

# View logs
docker-compose logs -f roman-numeral-service

# Stop all
docker-compose down

# Clean restart
docker-compose down -v && docker-compose up -d
```

---

## Observability

### Grafana Dashboard

**URL**: http://localhost:3000/d/roman-numeral-service

**Sections:**
| Row | Panels |
|-----|--------|
| 🔴 Critical Metrics | Availability SLA, Response Time, Error Rate |
| 📊 Request Metrics | Status codes, Latency percentiles |
| 💻 JVM Metrics | Memory, CPU, Threads, GC |
| 🔄 Range Processing | Processing time, Range sizes |
| 🔐 API Security | Validation success/failure |
| 📬 Kafka Metrics | Message rates |
| 📋 Logs | Live application logs |

### Metrics Endpoints

```bash
# Prometheus metrics
curl http://localhost:8081/actuator/prometheus

# Health check
curl http://localhost:8081/actuator/health

# Info
curl http://localhost:8081/actuator/info
```

---

## Security

### API Key Authentication

- **Storage**: PostgreSQL with BCrypt hashing
- **Validation**: O(n) comparison (n = active keys)
- **Metrics**: Success/failure counters in Prometheus

### Security Headers

- Content Security Policy (CSP)
- X-Frame-Options: DENY
- X-Content-Type-Options: nosniff
- Rate limiting per IP

### Production Configuration

```yaml
# application-prod.yml
app:
  api-security:
    enabled: true
  rate-limiting:
    requests-per-minute: 100

server:
  error:
    include-stacktrace: never  # Security: hide internals
```

See [docs/API_SECURITY.md](docs/API_SECURITY.md) for detailed documentation.

---

## Architecture Decision Records

All architectural decisions documented in [`docs/adr/`](docs/adr/):

| ADR | Decision | Status |
|-----|----------|--------|
| [001](docs/adr/001-precomputed-cache.md) | Pre-computed Cache | Active |
| [002](docs/adr/002-virtual-threads.md) | Virtual Threads | Partially Superseded |
| [003](docs/adr/003-plain-text-errors.md) | Plain Text Errors | Active |
| [004](docs/adr/004-rate-limiting.md) | Bucket4j Rate Limiting | Active |
| [005](docs/adr/005-observability-stack.md) | PLG Stack | Active |
| [006](docs/adr/006-no-database.md) | No Database | Superseded |
| [007](docs/adr/007-array-cache-optimization.md) | Array Cache | Active |
| [008](docs/adr/008-hybrid-threading-strategy.md) | Hybrid Threading | Active |
| [009](docs/adr/009-pagination.md) | Offset Pagination | Active |
| [010](docs/adr/010-api-key-authentication.md) | API Key Auth | Active |
| [011](docs/adr/011-event-driven-architecture.md) | Kafka Events | Active |
| [012](docs/adr/012-unified-lakehouse-architecture.md) | Lakehouse | Active |
| [013](docs/adr/013-processing-engine-selection.md) | Flink + Spark | Active |
| [014](docs/adr/014-data-quality-framework.md) | Great Expectations | Active |
| [015](docs/adr/015-data-lineage.md) | Marquez + OpenLineage | Active |

---

## Dependencies

### Runtime

| Dependency | Version | Purpose |
|------------|---------|---------|
| Spring Boot | 3.4.1 | Application framework |
| Spring Data JPA | 3.4.1 | Database access |
| Spring Kafka | 3.4.1 | Event streaming |
| PostgreSQL | 42.x | Database driver |
| Micrometer | 1.12.x | Metrics |
| Bucket4j | 8.10.1 | Rate limiting |
| SpringDoc OpenAPI | 2.7.0 | API documentation |

### Data Platform

| Technology | Version | Purpose |
|------------|---------|---------|
| Apache Kafka | 3.6 | Event streaming |
| Apache Flink | 1.18 | Stream processing |
| Apache Spark | 3.5 | Batch processing |
| Apache Iceberg | 1.5 | Table format |
| Apache Airflow | 2.8 | Orchestration |
| Hive Metastore | 3.1 | Catalog |
| MinIO | Latest | Object storage |
| Great Expectations | 0.18 | Data quality |
| Marquez | 0.47 | Data lineage |
| Apache Superset | 3.1 | BI dashboards |

---

## Project Structure

```
roman-numeral-service/
├── src/main/java/com/adobe/romannumeral/
│   ├── controller/       # REST endpoints
│   ├── service/          # Business logic
│   ├── converter/        # Conversion algorithm
│   ├── model/            # DTOs (records)
│   ├── entity/           # JPA entities
│   ├── repository/       # Data access
│   ├── filter/           # HTTP filters
│   ├── event/            # Kafka events
│   ├── config/           # Configuration
│   └── exception/        # Error handling
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── application-prod.yml
├── data-platform/
│   ├── flink/            # Flink jobs
│   ├── spark/            # Spark jobs
│   ├── airflow/          # DAGs
│   ├── great_expectations/
│   └── notebooks/        # Jupyter
├── docker/
│   ├── grafana/
│   ├── prometheus/
│   ├── loki/
│   └── superset/
├── docs/
│   ├── adr/              # Decision records
│   └── API_SECURITY.md
├── load-tests/           # k6 tests
├── docker-compose.yml
└── README.md
```

---

## License

This project was created for the Adobe AEM Engineering Assessment.

---

## Author

Created by Janakiraman for the Adobe AEM Engineering team assessment.
