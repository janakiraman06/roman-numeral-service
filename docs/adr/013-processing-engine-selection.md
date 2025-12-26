# ADR-013: Processing Engine Selection (Flink + Spark Hybrid)

**Status**: Accepted  
**Date**: 2024-12-25  
**Deciders**: Engineering Team  
**Technical Story**: Select optimal processing engines for streaming and batch workloads

## Context and Problem Statement

The Roman Numeral Service data lakehouse requires two types of data processing:

1. **Real-time Streaming**: Ingest events from Kafka to Bronze layer with low latency
2. **Batch Processing**: Transform data through Silver and Gold layers with complex logic

We need to select processing engines that provide:
- Exactly-once semantics for data consistency
- Efficient state management for deduplication
- Watermarks and late arrival handling for event-time processing
- Scalable batch processing for SCD Type 2 and aggregations

## Decision Drivers

1. **Streaming Excellence** - True event-at-a-time processing for Bronze
2. **Batch Excellence** - Mature SQL/DataFrame API for Silver/Gold
3. **Exactly-Once Guarantees** - Data consistency across layers
4. **State Management** - Efficient deduplication and windowing
5. **Industry Best Practices** - Follow patterns used at scale
6. **Right Tool for the Job** - Avoid one-size-fits-all approach

## Considered Options

### Option A: Spark for Everything

```
┌─────────────────────────────────────────────────────────────┐
│                 Spark Structured Streaming                  │
│                                                             │
│   Kafka ──▶ Spark (micro-batch) ──▶ Bronze ──▶ Silver ──▶ Gold │
│                                                             │
│   Latency: Seconds (trigger interval)                       │
│   Semantics: Exactly-once (with checkpointing)              │
└─────────────────────────────────────────────────────────────┘
```

**Pros:**
- Single engine, simpler operations
- Mature Iceberg integration
- Large ecosystem and community

**Cons:**
- Micro-batch, not true streaming
- State management less efficient for streaming
- Streaming is an add-on, not core strength

### Option B: Flink for Everything

```
┌─────────────────────────────────────────────────────────────┐
│                    Flink Unified Engine                     │
│                                                             │
│   Kafka ──▶ Flink (event-at-a-time) ──▶ Bronze ──▶ Silver ──▶ Gold │
│                                                             │
│   Latency: Milliseconds                                     │
│   Semantics: Exactly-once (checkpoint barriers)             │
└─────────────────────────────────────────────────────────────┘
```

**Pros:**
- True streaming semantics
- Excellent state management (RocksDB)
- Single engine

**Cons:**
- Batch processing less mature than Spark
- Smaller ecosystem for analytics
- Steeper learning curve for batch

### Option C: Flink + Spark Hybrid ✅

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     Hybrid Architecture                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌──────────────────────────────────────────────────────────────────┐ │
│   │              STREAMING (Apache Flink)                            │ │
│   │                                                                  │ │
│   │   Kafka ──▶ Flink ──▶ Bronze (Iceberg)                          │ │
│   │            │                                                     │ │
│   │            ├─ Watermarks (5 min delay)                          │ │
│   │            ├─ Deduplication (event_id)                          │ │
│   │            ├─ Late arrival handling (1 hr window)               │ │
│   │            └─ Dead Letter Queue (errors)                        │ │
│   │                                                                  │ │
│   │   Latency: Milliseconds                                         │ │
│   │   Semantics: Exactly-once (checkpoints + RocksDB)               │ │
│   └──────────────────────────────────────────────────────────────────┘ │
│                                │                                        │
│                                ▼                                        │
│   ┌──────────────────────────────────────────────────────────────────┐ │
│   │              BATCH (Apache Spark)                                │ │
│   │                                                                  │ │
│   │   Bronze ──▶ Spark ──▶ Silver ──▶ Spark ──▶ Gold                │ │
│   │              │                    │                              │ │
│   │              ├─ SCD Type 2        ├─ Star Schema                │ │
│   │              ├─ Data validation   ├─ Aggregations               │ │
│   │              └─ MERGE INTO        └─ Materialized views         │ │
│   │                                                                  │ │
│   │   Schedule: Hourly (Silver), Daily (Gold) via Airflow          │ │
│   │   Semantics: Idempotent batch writes                            │ │
│   └──────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

**Pros:**
- Best tool for each job
- True streaming for Bronze (Flink)
- Mature batch for Silver/Gold (Spark)
- Industry-proven pattern (Uber, LinkedIn)

**Cons:**
- Two engines to manage
- More operational complexity
- Team needs both skill sets

## Decision

We chose **Option C: Flink + Spark Hybrid** because:

1. **Streaming-First for Ingestion**: Flink's event-at-a-time model is superior for Bronze layer where we need:
   - True watermarks for event-time processing
   - Efficient keyed state for deduplication
   - Low-latency exactly-once ingestion

2. **Batch-Optimized for Transformations**: Spark excels at Silver/Gold where we need:
   - Complex SQL transformations
   - SCD Type 2 implementation
   - Large-scale aggregations
   - Mature Iceberg support

3. **Industry Validation**: This pattern is used by:
   - **Uber**: Flink for real-time, Spark for batch analytics
   - **LinkedIn**: Flink streaming, Spark for offline processing
   - **Netflix**: Similar hybrid approaches

## Comparison Matrix

| Aspect | Flink (Bronze) | Spark (Silver/Gold) |
|--------|----------------|---------------------|
| **Model** | Event-at-a-time | Batch/Micro-batch |
| **Latency** | Milliseconds | Seconds-Minutes |
| **State** | RocksDB (incremental) | Checkpoint-based |
| **SQL** | Flink SQL | Spark SQL (mature) |
| **Windowing** | Event-time native | Requires watermarks |
| **Deduplication** | Keyed state with TTL | MERGE INTO |
| **Late Data** | First-class support | Append + reprocess |

## Data Engineering Standards

### Flink (Bronze Layer)

```
┌─────────────────────────────────────────────────────────────┐
│                    Bronze Ingestion Standards               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. EXACTLY-ONCE SEMANTICS                                  │
│     - Flink checkpointing (60s interval)                    │
│     - RocksDB state backend                                 │
│     - Kafka consumer offset management                      │
│                                                             │
│  2. LATE ARRIVING DATA                                      │
│     - Watermark: event_timestamp - 5 minutes                │
│     - Allowed lateness: 1 hour                              │
│     - Late events flagged (is_late_arrival = true)          │
│                                                             │
│  3. DEDUPLICATION                                           │
│     - Key: event_id                                         │
│     - State TTL: 24 hours                                   │
│     - First-event-wins semantics                            │
│                                                             │
│  4. ERROR HANDLING                                          │
│     - Dead Letter Queue: roman-numeral-events-dlq           │
│     - Error metadata preserved                              │
│     - Monitoring and alerting                               │
│                                                             │
│  5. FAILURE RECOVERY                                        │
│     - Automatic restart (3 attempts, 30s delay)             │
│     - Checkpoint-based recovery                             │
│     - Savepoints for upgrades                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Spark (Silver/Gold Layers)

```
┌─────────────────────────────────────────────────────────────┐
│                    Silver/Gold ETL Standards                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. IDEMPOTENT PROCESSING                                   │
│     - MERGE INTO for upserts                                │
│     - Processing date as partition key                      │
│     - Rerunnable without side effects                       │
│                                                             │
│  2. BACKFILL STRATEGY                                       │
│     - Parameterized date ranges                             │
│     - Write to staging, then swap                           │
│     - Maintain audit trail                                  │
│                                                             │
│  3. DATA QUALITY                                            │
│     - Great Expectations validation                         │
│     - Schema enforcement                                    │
│     - Null handling rules                                   │
│                                                             │
│  4. SCD TYPE 2 (Silver)                                     │
│     - dim_users with valid_from/valid_to                    │
│     - is_current flag for latest version                    │
│     - Preserve full history                                 │
│                                                             │
│  5. ORCHESTRATION                                           │
│     - Airflow DAGs with dependencies                        │
│     - Sensor for Bronze data availability                   │
│     - Retry and alerting policies                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Consequences

### Positive

- **Optimal Performance**: Each engine used for its strength
- **True Streaming**: Flink provides real event-time processing
- **Mature Batch**: Spark's SQL engine is production-proven
- **Scalability**: Both engines scale horizontally
- **Industry Alignment**: Pattern used by leading tech companies

### Negative

- **Operational Complexity**: Two engines to monitor
- **Skill Requirements**: Team needs Flink and Spark expertise
- **Integration Overhead**: Shared state via Iceberg tables

### Risks

- **Version Compatibility**: Ensure Flink/Spark Iceberg versions align
- **Checkpoint Storage**: Both need S3/MinIO access
- **Resource Contention**: May compete for cluster resources

## Implementation

### Docker Services

| Service | Port | Purpose |
|---------|------|---------|
| `flink-jobmanager` | 8092 | Flink Web UI, job coordination |
| `flink-taskmanager` | - | Task execution, state management |
| `spark-master` | 8090 | Spark Web UI, job coordination |
| `spark-worker` | 8091 | Task execution |

### Job Submission

```bash
# Build Flink Bronze JAR (from host)
cd data-platform/flink/bronze-ingestion && mvn clean package -DskipTests

# Flink Bronze Streaming (Java 11)
docker exec flink-jobmanager flink run \
  -c com.adobe.lakehouse.bronze.job.BronzeIngestionJob \
  /opt/flink-jobs/bronze-ingestion/target/bronze-ingestion-1.0.0.jar

# Spark Silver/Gold Batch (via Airflow)
docker exec spark-master spark-submit --master spark://spark-master:7077 \
  /opt/spark-jobs/silver_etl.py
```

### Language Choices

| Component | Language | Rationale |
|-----------|----------|-----------|
| Spring Boot App | Java 21 | Latest LTS with virtual threads |
| Flink Bronze | Java 17 | [Flink recommended version](https://nightlies.apache.org/flink/flink-docs-master/docs/deployment/java_compatibility/), Docker default |
| Spark Batch | PySpark/Scala | Flexible for data engineering |

The Java Flink job runs in a separate JVM (Docker container), so the different
Java versions don't conflict. This is standard practice in production environments.

## Related ADRs

- [ADR-011: Event-Driven Architecture](011-event-driven-architecture.md) - Kafka event streaming
- [ADR-012: Unified Lakehouse Architecture](012-unified-lakehouse-architecture.md) - Overall data architecture

## References

- [Flink vs Spark: A Detailed Comparison](https://www.confluent.io/blog/apache-flink-apache-spark-comparing-two-stream-processing-platforms/)
- [Uber's Real-Time Data Infrastructure](https://eng.uber.com/uber-big-data-platform/)
- [LinkedIn's Lambda Architecture with Flink](https://engineering.linkedin.com/blog/2019/06/stream-processing-architecture)
- [Apache Flink Documentation](https://flink.apache.org/docs/)
- [Apache Spark Documentation](https://spark.apache.org/docs/)

