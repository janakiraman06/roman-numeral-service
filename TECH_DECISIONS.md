# Technology Decisions

This document outlines the technology choices made for the Roman Numeral Service, including alternatives considered and rationale for each decision.

## Table of Contents

1. [Programming Language](#1-programming-language)
2. [Java Version](#2-java-version)
3. [Framework](#3-framework)
4. [Build Tool](#4-build-tool)
5. [Testing Framework](#5-testing-framework)
6. [Metrics Stack](#6-metrics-stack)
7. [Logging Stack](#7-logging-stack)
8. [Rate Limiting](#8-rate-limiting)
9. [Load Testing](#9-load-testing)
10. [Container Runtime](#10-container-runtime)
11. [Base Docker Image](#11-base-docker-image)
12. [Caching Strategy](#12-caching-strategy)

---

## 1. Programming Language

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Java** | Strong typing, AEM uses Java, enterprise standard, excellent tooling | More verbose | ✅ **SELECTED** |
| JavaScript/Node.js | Fast development, async-native, lightweight | Weak typing, less enterprise adoption | Considered |
| Kotlin | Modern Java alternative, concise syntax, null safety | Smaller talent pool, extra complexity | Not selected |

**Rationale:** Adobe AEM is Java-based. Choosing Java demonstrates alignment with their tech stack and enterprise-grade engineering practices.

---

## 2. Java Version

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Java 21 LTS** | Virtual threads, pattern matching, records, LTS until 2031 | Newer, less enterprise adoption | ✅ **SELECTED** |
| Java 17 LTS | Widely adopted, stable, LTS until 2029 | Missing virtual threads | Strong alternative |
| Java 11 LTS | Very stable, broad adoption | Missing modern features, EOL concerns | Legacy choice |

**Rationale:** Java 21 provides virtual threads (Project Loom) which simplify our parallel processing implementation. As an LTS release, it's production-safe while demonstrating cutting-edge knowledge.

**Interview Talking Point:** "I chose Java 21 for virtual threads, which elegantly solved the parallel processing requirement without complex thread pool management."

---

## 3. Framework

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Spring Boot** | Industry standard, massive ecosystem, Actuator built-in, excellent docs | Heavier startup, more memory | ✅ **SELECTED** |
| Quarkus | Fast startup, low memory, GraalVM native support | Smaller community, less mature | Cloud-native alternative |
| Micronaut | Compile-time DI, low memory, fast startup | Smaller ecosystem, different patterns | Considered |

**Rationale:** Spring Boot is the industry standard for Java microservices. Built-in Actuator provides production-ready metrics, health checks, and monitoring with minimal configuration.

---

## 4. Build Tool

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Maven** | Standardized, declarative, excellent IDE support, widely understood | XML verbose, less flexible | ✅ **SELECTED** |
| Gradle | Flexible, faster builds, Kotlin/Groovy DSL | Steeper learning curve, script complexity | Strong alternative |
| Ant | Full control, simple tasks | Legacy, no dependency management | Not recommended |

**Rationale:** Maven's convention-over-configuration approach makes the project immediately understandable to reviewers. Its standardized structure is universally recognized.

---

## 5. Testing Framework

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **JUnit 5** | Modern, extensible, parameterized tests, Spring integration | Requires Java 8+ | ✅ **SELECTED** |
| TestNG | Powerful grouping, parallel execution, data providers | Less Spring integration, declining | Considered |
| Spock | Expressive BDD syntax, readable specifications | Groovy dependency, different paradigm | Considered |

**Rationale:** JUnit 5's `@ParameterizedTest` is perfect for testing Roman numeral conversions with data-driven tests. Native Spring Boot integration simplifies testing.

---

## 6. Metrics Stack

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Micrometer + Prometheus + Grafana** | Free, industry standard, excellent Spring integration | Multiple components to manage | ✅ **SELECTED** |
| Dropwizard Metrics | Simple, lightweight, built-in reporters | Smaller ecosystem, older approach | Legacy option |
| OpenTelemetry | Unified observability, vendor neutral | More complex setup, newer | Future standard |

**Rationale:** Prometheus/Grafana is the de facto standard for cloud-native observability. Micrometer provides a vendor-neutral metrics facade with native Spring Boot Actuator integration.

---

## 7. Logging Stack

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Loki + Grafana** | Lightweight, free, unified UI with metrics, container-native | Less powerful queries than ELK | ✅ **SELECTED** |
| ELK Stack | Powerful full-text search, industry proven, Kibana | Heavy (4GB+ RAM), complex setup | Enterprise alternative |
| Splunk | Enterprise-grade, powerful analytics | Expensive licensing, overkill | Enterprise only |

**Rationale:** Loki provides a "single pane of glass" with Grafana for both metrics and logs. It's lightweight enough for local development while being production-ready.

**Trade-off:** We sacrificed ELK's powerful full-text search for Loki's simplicity and unified dashboard experience.

---

## 8. Rate Limiting

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Bucket4j** | Token bucket algorithm, Spring integration, in-memory/distributed | Configuration required | ✅ **SELECTED** |
| Resilience4j | Circuit breaker + rate limiter, comprehensive patterns | More complex, may be overkill | Considered |
| Guava RateLimiter | Simple, Google-backed, lightweight | Limited features, no distributed | Minimal option |

**Rationale:** Bucket4j is purpose-built for rate limiting with a clean API. It can scale from in-memory (current) to Redis-backed (production) without code changes.

---

## 9. Load Testing

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **k6** | Modern, lightweight, Grafana integration, CI/CD friendly | JavaScript-based | ✅ **SELECTED** |
| Gatling | JVM ecosystem, powerful DSL, beautiful reports | Scala learning curve | Java alternative |
| JMeter | Industry standard, GUI, extensive protocols | Heavy, dated UI, XML configs | Traditional choice |

**Rationale:** k6 integrates with our Grafana stack and is designed for modern CI/CD pipelines. Its JavaScript DSL is easy to read and maintain.

---

## 10. Container Runtime

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Docker** | Industry standard, universal adoption, Docker Compose | Daemon-based, licensing | ✅ **SELECTED** |
| Podman | Daemonless, rootless, Docker-compatible | Less tooling, smaller ecosystem | Linux alternative |
| containerd | Lightweight, Kubernetes native | Lower-level, no build tooling | K8s runtime only |

**Rationale:** Docker is universally understood and expected. Docker Compose simplifies local development with the full observability stack.

---

## 11. Base Docker Image

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Eclipse Temurin Alpine** | Small (~200MB), production-ready, well-maintained | Alpine musl libc edge cases | ✅ **SELECTED** |
| Eclipse Temurin (Debian) | glibc compatibility, most compatible | Larger image (~400MB) | Compatibility fallback |
| Distroless | Minimal attack surface, Google-maintained | No shell for debugging | Security-focused |

**Rationale:** Temurin Alpine balances small image size with debuggability. Eclipse Temurin is the successor to AdoptOpenJDK with strong community support.

---

## 12. Caching Strategy

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Pre-computed Cache** | O(1) lookup, thread-safe, no external deps | O(n) memory, startup cost | ✅ **SELECTED** |
| Lazy Caffeine Cache | On-demand computation, eviction policies | Synchronization complexity | Considered |
| No Cache | Simplest, minimal memory | Repeated computation | Baseline |

**Rationale:** With only 3999 possible values (~40KB), pre-computing all conversions at startup trades negligible memory for guaranteed O(1) lookups. The immutable cache is inherently thread-safe.

**Complexity Analysis:**
- Space: O(n) where n = 3999 entries ≈ 40KB
- Time: O(1) per lookup after O(n) initialization

---

## Summary: Selected Stack

| Category | Choice | Key Reason |
|----------|--------|------------|
| Language | Java 21 | Virtual threads, AEM alignment |
| Framework | Spring Boot 3.4.x | Industry standard, Actuator |
| Build | Maven | Universal, simple |
| Testing | JUnit 5 | Parameterized tests |
| Metrics | Prometheus + Grafana | Free, powerful |
| Logging | Loki + Grafana | Unified observability |
| Rate Limiting | Bucket4j | Purpose-built |
| Load Testing | k6 | Modern, Grafana native |
| Container | Docker + Compose | Universal standard |
| Base Image | Temurin 21 Alpine | Small, production-ready |
| Caching | Pre-computed Map | O(1) thread-safe lookups |

---

## Design Principles Applied

| Principle | Application |
|-----------|-------------|
| **SOLID** | Interface-based converter (DIP, OCP, LSP) |
| **DRY** | Reusable converter across service methods |
| **KISS** | Simple greedy algorithm, no over-engineering |
| **YAGNI** | No unused features or abstractions |

---

## Production Considerations

If this were a production service, additional considerations would include:

1. **Authentication**: OAuth2 or API keys for access control
2. **Distributed Rate Limiting**: Redis-backed Bucket4j
3. **Error Responses**: RFC 7807 Problem Details format
4. **Distributed Tracing**: OpenTelemetry integration
5. **Database**: If persistence were needed
6. **Kubernetes**: Helm charts, HPA, pod disruption budgets

