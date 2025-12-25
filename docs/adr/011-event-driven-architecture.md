# ADR-011: Event-Driven Architecture with Apache Kafka

## Status
Accepted

## Date
2024-12-25

## Context

The Roman Numeral Service needs to support a data engineering platform for analytics:
1. **Usage Analytics** - Track conversion patterns, popular numbers, peak times
2. **User Behavior** - Monitor API usage per client/key
3. **Performance Monitoring** - Analyze response times and throughput
4. **Data Lake Ingestion** - Stream events to Bronze layer for batch processing

### Decision Drivers

- **Decoupling**: Analytics should not affect API latency
- **Scalability**: Handle high-volume event streams
- **Durability**: Events must not be lost
- **Flexibility**: Support multiple downstream consumers
- **Real-time + Batch**: Enable both streaming and batch analytics

## Considered Options

### Option 1: Direct Database Writes
- **Pros**: Simple, transactional consistency
- **Cons**: Tight coupling, latency impact, schema rigidity

### Option 2: Log File Streaming (Fluentd/Filebeat)
- **Pros**: Non-intrusive, works with existing logs
- **Cons**: Parsing complexity, unstructured data, delayed

### Option 3: Apache Kafka
- **Pros**: High throughput, durable, decoupled, ecosystem support
- **Cons**: Additional infrastructure, operational complexity

### Option 4: Cloud Managed (Kinesis/Pub/Sub)
- **Pros**: Managed, scalable
- **Cons**: Vendor lock-in, not suitable for local development

## Decision

**Chosen: Apache Kafka** for event streaming.

Kafka provides the right balance of performance, durability, and ecosystem compatibility for our Unified Lakehouse architecture.

## Implementation Details

### Event Flow Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         API Request Flow                                 │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Roman Numeral Controller                           │
│                                                                         │
│  1. Process request                                                     │
│  2. Generate response                                                   │
│  3. Publish event (async, non-blocking)                                │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ (async)
┌─────────────────────────────────────────────────────────────────────────┐
│                      KafkaProducerService                               │
│                                                                         │
│  - Serializes ConversionEvent to JSON                                  │
│  - Publishes to 'romannumeral-events' topic                            │
│  - Fire-and-forget (errors logged, don't block response)              │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Apache Kafka                                    │
│                                                                         │
│  Topic: romannumeral-events                                            │
│  Partitions: 3                                                         │
│  Retention: 7 days                                                     │
└─────────────────────────────────────────────────────────────────────────┘
                           │              │
                           ▼              ▼
              ┌────────────────┐  ┌────────────────────┐
              │ Spark Streaming│  │ Batch Processing   │
              │ (Real-time)    │  │ (Airflow + Spark)  │
              └───────┬────────┘  └─────────┬──────────┘
                      │                     │
                      ▼                     ▼
              ┌─────────────────────────────────────────┐
              │           Apache Iceberg                │
              │     (Bronze → Silver → Gold)            │
              └─────────────────────────────────────────┘
```

### Event Schema

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventTime": "2024-12-25T10:30:00.000Z",
  "eventType": "SINGLE",
  "userId": 123,
  "apiKeyPrefix": "rns_abc12345",
  "clientIp": "192.168.1.100",
  "correlationId": "req-xyz-789",
  "inputNumber": 42,
  "outputRoman": "XLII",
  "rangeMin": null,
  "rangeMax": null,
  "resultCount": null,
  "pageOffset": null,
  "pageLimit": null,
  "responseTimeNanos": 125000,
  "status": "SUCCESS"
}
```

### Kafka Configuration

```yaml
# KRaft mode (no Zookeeper required)
KAFKA_CFG_PROCESS_ROLES: broker,controller
KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE: true
KAFKA_CFG_NUM_PARTITIONS: 3
KAFKA_CFG_LOG_RETENTION_HOURS: 168  # 7 days
```

### Producer Configuration

| Setting | Value | Rationale |
|---------|-------|-----------|
| acks | 1 | Balance durability and latency |
| retries | 3 | Handle transient failures |
| key | eventType-input | Partition by event type |
| async | true | Non-blocking, fire-and-forget |

### Topic Design

| Topic | Purpose | Partitions | Retention |
|-------|---------|------------|-----------|
| romannumeral-events | All conversion events | 3 | 7 days |

### Failure Handling

Since event publishing is non-critical:
1. **Async publishing** - Does not block HTTP response
2. **Error logging** - Failed publishes are logged for debugging
3. **Metrics** - Track success/failure rates via Prometheus
4. **No retry queue** - Accept some loss for simplicity

## Consequences

### Positive
- Zero latency impact on API requests
- Decoupled analytics pipeline
- Supports both real-time and batch processing
- 7-day replay capability
- Horizontally scalable

### Negative
- Additional infrastructure (Kafka)
- Eventual consistency for analytics
- At-most-once delivery (acceptable for analytics)

### Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Kafka unavailable | Graceful degradation, events logged |
| Message loss | Accept for analytics use case |
| Schema evolution | JSON with optional fields |
| Topic saturation | Monitor lag, scale partitions |

## Metrics

| Metric | Type | Description |
|--------|------|-------------|
| kafka.events.published{status=success} | Counter | Successful publishes |
| kafka.events.published{status=failure} | Counter | Failed publishes |

## Future Enhancements

1. **Dead Letter Queue**: For failed events requiring investigation
2. **Schema Registry**: Avro/Protobuf for stricter contracts
3. **Exactly-Once Semantics**: If analytics require precision
4. **Multi-Topic**: Separate topics by event type

## References

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Kafka](https://spring.io/projects/spring-kafka)
- [Event-Driven Architecture Patterns](https://martinfowler.com/articles/201701-event-driven.html)

