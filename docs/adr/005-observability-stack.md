# ADR-005: Use Prometheus, Grafana, and Loki for Observability

## Status

Accepted

## Date

2024-12-24

## Context

The specification requires "production-grade" observability. We need:

- **Metrics**: Request rates, latencies, error rates, JVM stats
- **Logging**: Centralized, searchable logs with correlation
- **Visualization**: Dashboards for monitoring
- **Alerting**: (Optional) Notification on anomalies

## Decision Drivers

- Cloud-native and industry-standard tools
- Open-source with active communities
- Works well with Spring Boot
- Docker-compose friendly for local development
- Demonstrates DevOps/SRE knowledge

## Considered Options

### Metrics

| Option | Pros | Cons |
|--------|------|------|
| **Prometheus** ✓ | Industry standard, pull-based, PromQL | Requires scrape config |
| Datadog | Managed, rich features | Commercial, cost |
| InfluxDB | Time-series native | Less ecosystem |

### Logging

| Option | Pros | Cons |
|--------|------|------|
| **Loki** ✓ | Lightweight, labels, Grafana native | Less feature-rich than ELK |
| ELK Stack | Powerful search, widely used | Heavy resource usage |
| Splunk | Enterprise features | Commercial, expensive |

### Visualization

| Option | Pros | Cons |
|--------|------|------|
| **Grafana** ✓ | Universal, beautiful dashboards | Learning curve |
| Kibana | ELK native | Only for Elasticsearch |
| Custom | Full control | Development effort |

## Decision

**Use Prometheus + Loki + Grafana (PLG Stack).**

This combination provides:

- **Prometheus**: Metrics collection via scraping
- **Loki**: Log aggregation with label-based indexing
- **Grafana**: Unified visualization for metrics and logs

```
┌─────────────────┐
│ Spring Boot App │
│                 │
│  /actuator/     │────scrape────► Prometheus ────┐
│  prometheus     │                               │
│                 │                               │
│  stdout/stderr  │────docker────► Loki ─────────┤
└─────────────────┘               driver          │
                                                  ▼
                                            ┌──────────┐
                                            │ Grafana  │
                                            └──────────┘
```

## Consequences

### Positive

- **Industry standard**: Widely adopted in cloud-native environments
- **Unified view**: Single dashboard for metrics and logs
- **Cost-effective**: All open-source
- **Lightweight**: Loki uses minimal resources compared to ELK
- **Spring Boot integration**: Native Micrometer support
- **Docker-friendly**: Easy to orchestrate with docker-compose

### Negative

- **Learning curve**: PromQL and LogQL query languages
- **Configuration**: Multiple services to configure
- **Resource usage**: Adds ~500MB RAM for full stack

### Risks

- **Data volume**: High-traffic logs could fill storage
- **Mitigation**: Log retention policies, sampling

## Implementation Details

### Metrics Export

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### Key Metrics

| Metric | Type | Purpose |
|--------|------|---------|
| `http_server_requests_seconds` | Histogram | Request latency |
| `jvm_memory_used_bytes` | Gauge | Memory usage |
| `jvm_threads_live_threads` | Gauge | Thread count |
| Custom: conversion rate | Counter | Business metric |

### Log Aggregation

Using Loki Docker driver:

```yaml
# docker-compose.yml
services:
  app:
    logging:
      driver: loki
      options:
        loki-url: "http://loki:3100/loki/api/v1/push"
```

### Grafana Dashboard

Pre-provisioned dashboard includes:

- **SLA Row**: Availability, p99 latency, error rates
- **Request Row**: Throughput, status distribution
- **JVM Row**: Memory, CPU, GC, threads
- **Logs Row**: Real-time application logs

## Alternatives Considered

### ELK Stack (Elasticsearch, Logstash, Kibana)

```
App → Logstash → Elasticsearch → Kibana
```

**Why not chosen:**
- Higher resource requirements (Elasticsearch is heavy)
- More complex setup
- Overkill for single-service deployment

### Datadog/New Relic

**Why not chosen:**
- Commercial products
- Assessment context favors open-source demonstration

## Scaling Considerations

For production at scale:

1. **Prometheus**: Add Thanos for long-term storage
2. **Loki**: Configure object storage (S3/GCS)
3. **Grafana**: HA setup with PostgreSQL backend

## Compliance

- Meets "production-grade observability" requirement
- Demonstrates cloud-native DevOps practices
- Provides full visibility into application health

## Notes

- Prometheus: https://prometheus.io/
- Grafana: https://grafana.com/
- Loki: https://grafana.com/oss/loki/
- Spring Boot Actuator provides automatic metrics

