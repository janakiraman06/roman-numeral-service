# Data Architecture: Roman Numeral Service Data Platform

This document explains the data ingestion and processing architecture, covering both the production design and demo implementation.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DATA PLATFORM ARCHITECTURE                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   ┌─────────┐    ┌─────────┐    ┌─────────────────────────────────────────┐    │
│   │   API   │───▶│  Kafka  │───▶│              INGESTION                  │    │
│   └─────────┘    └─────────┘    │  ┌─────────────────────────────────┐   │    │
│                                 │  │ Production: Flink (real-time)   │   │    │
│                                 │  │ Demo:       Spark (batch)       │   │    │
│                                 │  └─────────────────────────────────┘   │    │
│                                 └──────────────────┬──────────────────────┘    │
│                                                    │                           │
│                                                    ▼                           │
│   ┌─────────────────────────────────────────────────────────────────────┐     │
│   │                        MEDALLION ARCHITECTURE                        │     │
│   ├─────────────────────────────────────────────────────────────────────┤     │
│   │                                                                     │     │
│   │   ┌──────────┐         ┌──────────┐         ┌──────────┐          │     │
│   │   │  BRONZE  │────────▶│  SILVER  │────────▶│   GOLD   │          │     │
│   │   │  (Raw)   │  Spark  │ (Clean)  │  Spark  │(Analytics)│          │     │
│   │   └──────────┘  Hourly └──────────┘  Daily  └──────────┘          │     │
│   │                                                                     │     │
│   │   Iceberg Tables on MinIO (S3-compatible)                          │     │
│   │                                                                     │     │
│   └─────────────────────────────────────────────────────────────────────┘     │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Bronze Layer Ingestion: Production vs Demo

### Production Architecture (Flink Primary)

In production, **Flink is the primary ingestion path** for Bronze layer:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         PRODUCTION BRONZE INGESTION                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   PRIMARY PATH (Real-time):                                                     │
│   ┌───────┐    ┌─────────┐    ┌─────────┐    ┌─────────────────────────┐       │
│   │  API  │───▶│  Kafka  │───▶│  Flink  │───▶│  Iceberg Bronze Table  │       │
│   └───────┘    └─────────┘    └─────────┘    └─────────────────────────┘       │
│                                    │                                            │
│                                    ├─ Sub-second latency                       │
│                                    ├─ Exactly-once (checkpointing)             │
│                                    ├─ Late data handling (watermarks)          │
│                                    └─ Stateful deduplication                   │
│                                                                                 │
│   SECONDARY PATH (Backfill/Recovery):                                          │
│   ┌─────────┐    ┌─────────────┐    ┌─────────────────────────┐               │
│   │  Kafka  │───▶│ Spark Batch │───▶│  Iceberg Bronze Table  │               │
│   └─────────┘    └─────────────┘    └─────────────────────────┘               │
│                        │                                                        │
│                        ├─ Historical reprocessing                              │
│                        ├─ Schema migration                                     │
│                        └─ Flink downtime recovery                              │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Demo Architecture (Spark Primary)

In the demo environment, due to local Docker dependency challenges with Flink+Iceberg, the roles are adjusted:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DEMO BRONZE INGESTION                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   FLINK (Streaming Demo):                                                       │
│   ┌───────┐    ┌─────────┐    ┌─────────┐    ┌─────────────────────────┐       │
│   │  API  │───▶│  Kafka  │───▶│  Flink  │───▶│  Console Output (logs) │       │
│   └───────┘    └─────────┘    └─────────┘    └─────────────────────────┘       │
│                                    │                                            │
│                                    ├─ Demonstrates Kafka consumption           │
│                                    ├─ Shows watermarking & deduplication       │
│                                    ├─ Checkpoints visible in Flink UI         │
│                                    └─ Viewable via: docker logs flink-taskmanager │
│                                                                                 │
│   SPARK (Bronze Ingestion):                                                     │
│   ┌─────────┐    ┌─────────────┐    ┌─────────────────────────┐               │
│   │  Kafka  │───▶│ Spark Batch │───▶│  Iceberg Bronze Table  │               │
│   └─────────┘    └─────────────┘    └─────────────────────────┘               │
│                        │                                                        │
│                        ├─ Runs every 15 min via Airflow                        │
│                        ├─ Incremental: tracks MAX(kafka_offset)                │
│                        ├─ Deduplication via MERGE INTO                         │
│                        └─ Working end-to-end data flow                         │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Why This Approach?

| Aspect | Explanation |
|--------|-------------|
| **Flink Demonstration** | Shows understanding of production streaming patterns |
| **Working E2E Pipeline** | Spark ensures data flows through Bronze → Silver → Gold |
| **Reviewer Verification** | Can verify both Flink (UI/logs) and data pipeline (Jupyter) |
| **Local Simplicity** | Avoids complex Flink+Iceberg dependency management in Docker |

---

## Why Kafka Connect is NOT Needed

A common question: "Why not use Kafka Connect to land data to files?"

**Answer: Flink provides everything Kafka Connect does, plus more.**

| Capability | Kafka Connect | Flink |
|------------|---------------|-------|
| Continuous ingestion | ✅ | ✅ |
| Kafka offset tracking | ✅ | ✅ (checkpoints) |
| Exactly-once semantics | ⚠️ Requires careful config | ✅ Native support |
| Late data handling | ❌ | ✅ Watermarks |
| Deduplication | ❌ | ✅ Stateful processing |
| Transformations | ❌ (only SMT) | ✅ Full SQL/DataStream API |
| Direct Iceberg writes | ⚠️ Newer connector | ✅ Mature support |
| Window aggregations | ❌ | ✅ |
| Complex event processing | ❌ | ✅ |

**Conclusion:** When using Flink for streaming, Kafka Connect becomes redundant.

### Alternative: Kafka Connect Pattern

If not using Flink, an alternative production pattern would be:

```
Kafka → Kafka Connect (S3 Sink) → Raw Files (Parquet) → Spark → Iceberg
                                        │
                                        └── s3://raw/events/year=2025/month=01/day=15/hour=14/
```

This pattern is useful when:
- No Flink expertise in team
- Want decoupled ingestion and processing
- Need raw file backup before transformation

But it adds complexity and another component to manage.

---

## Exactly-Once Semantics

Each layer provides exactly-once guarantees:

### Bronze Layer

```
Incremental Read:
┌─────────────────────────────────────────────────────────────────┐
│  1. Query: SELECT MAX(kafka_offset) FROM bronze                 │
│  2. Read:  Kafka messages WHERE offset > max_offset             │
│  3. Dedupe: ROW_NUMBER() OVER (PARTITION BY event_id)           │
│  4. Write: MERGE INTO bronze ON event_id                        │
└─────────────────────────────────────────────────────────────────┘
Result: Same event_id never duplicated, even on retry
```

### Silver Layer

```
Interval-Based Processing:
┌─────────────────────────────────────────────────────────────────┐
│  1. Filter: WHERE ingested_at >= interval_start                 │
│                AND ingested_at < interval_end                   │
│  2. Dedupe: ROW_NUMBER() OVER (PARTITION BY event_id)           │
│  3. Write: MERGE INTO silver ON event_id                        │
└─────────────────────────────────────────────────────────────────┘
Result: Idempotent - same interval always produces same result
```

### Gold Layer

```
Aggregate Processing:
┌─────────────────────────────────────────────────────────────────┐
│  1. Filter: WHERE processed_at >= interval_start                │
│                AND processed_at < interval_end                  │
│  2. Aggregate: GROUP BY date, user_id                           │
│  3. Write: MERGE INTO gold ON (date, user_id)                   │
└─────────────────────────────────────────────────────────────────┘
Result: Re-running same interval updates (not duplicates) aggregates
```

---

## Backfill Support

Each DAG supports manual backfill via Airflow params:

### Bronze Backfill

```json
{
    "backfill": true,
    "start_date": "2025-01-01",
    "end_date": "2025-01-07"
}
```

Reads from earliest Kafka offset, filters by event_time, uses MERGE to avoid duplicates.

### Silver/Gold Backfill

```json
{
    "start_date": "2025-01-01T00:00:00",
    "end_date": "2025-01-07T00:00:00"
}
```

Overrides Airflow's `data_interval_start/end`, reprocesses specified range.

---

## Failure Recovery

| Failure Scenario | Recovery Mechanism |
|------------------|-------------------|
| **Bronze job fails mid-write** | Next run re-reads from last offset; MERGE skips existing |
| **Silver job fails** | Retry same interval; MERGE is idempotent |
| **Gold job fails** | Retry same interval; MERGE updates aggregates |
| **Kafka unavailable** | DAG retries (2x); alerts after exhausted |
| **Flink job crashes** | Resumes from last checkpoint; Spark handles backfill |
| **Spark OOM** | Increase executor memory; reduce batch size |

---

## Key Timestamps

| Layer | Timestamp | Meaning | Used For |
|-------|-----------|---------|----------|
| **Bronze** | `event_time` | When event occurred | Business logic |
| **Bronze** | `ingested_at` | When landed in Bronze | Silver filtering |
| **Bronze** | `kafka_offset` | Kafka position | Incremental reads |
| **Silver** | `processed_at` | When transformed | Gold filtering |
| **Gold** | `generated_at` | When aggregated | Freshness tracking |

---

## Summary

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              ARCHITECTURE SUMMARY                                │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   Component        │ Production              │ Demo                            │
│   ─────────────────┼─────────────────────────┼─────────────────────────────────│
│   Bronze Ingestion │ Flink (real-time)       │ Spark (batch, every 15 min)    │
│   Bronze Backfill  │ Spark (on-demand)       │ Spark (same DAG)               │
│   Silver ETL       │ Spark (hourly)          │ Spark (hourly)                 │
│   Gold ETL         │ Spark (daily)           │ Spark (daily)                  │
│   Kafka Connect    │ Not needed              │ Not needed                     │
│   Orchestration    │ Airflow                 │ Airflow                        │
│   Storage          │ S3 + Iceberg            │ MinIO + Iceberg                │
│   Catalog          │ AWS Glue / Nessie       │ Iceberg REST Catalog           │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

*Last Updated: December 26, 2025*

