# Data Platform

This directory contains the data engineering components for the Roman Numeral Service's Unified Lakehouse Architecture using a **Flink + Spark Hybrid** approach.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     Hybrid Processing Architecture                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌──────────────────────────────────────────────────────────────────────┐ │
│   │                    STREAMING (Apache Flink)                          │ │
│   │                                                                      │ │
│   │     Kafka  ────▶  Flink Job  ────▶  Bronze (Iceberg)                │ │
│   │                   │                                                  │ │
│   │                   ├─ Watermarks (5 min delay)                       │ │
│   │                   ├─ Deduplication (event_id, 24hr TTL)             │ │
│   │                   ├─ Late arrival handling (1 hr window)            │ │
│   │                   ├─ Exactly-once (checkpoints + RocksDB)           │ │
│   │                   └─ Dead Letter Queue (errors)                     │ │
│   │                                                                      │ │
│   │   Latency: Milliseconds | UI: http://localhost:8092                 │ │
│   └──────────────────────────────────────────────────────────────────────┘ │
│                                │                                            │
│                                ▼                                            │
│   ┌──────────────────────────────────────────────────────────────────────┐ │
│   │                    BATCH (Apache Spark via Airflow)                  │ │
│   │                                                                      │ │
│   │     Bronze  ────▶  Spark  ────▶  Silver  ────▶  Spark  ────▶  Gold  │ │
│   │                    │                           │                     │ │
│   │                    ├─ SCD Type 2               ├─ Star Schema       │ │
│   │                    ├─ Data validation          ├─ Aggregations      │ │
│   │                    └─ MERGE INTO               └─ Materialized views│ │
│   │                                                                      │ │
│   │   Schedule: Hourly (Silver), Daily (Gold) | UI: http://localhost:8090│ │
│   └──────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Why Flink + Spark Hybrid?

| Engine | Layer | Why |
|--------|-------|-----|
| **Flink** | Bronze | True event-at-a-time streaming, superior state management (RocksDB), first-class watermarks |
| **Spark** | Silver/Gold | Mature SQL engine, excellent Iceberg support, proven batch performance |

This pattern is used by **Uber**, **LinkedIn**, and other industry leaders. See [ADR-013](../docs/adr/013-processing-engine-selection.md) for detailed rationale.

## Directory Structure

```
data-platform/
├── README.md                           # This file
├── flink/
│   ├── conf/
│   │   └── flink-conf.yaml            # Flink configuration (checkpoints, S3)
│   └── bronze-ingestion/              # Java Flink project
│       ├── pom.xml                    # Maven build file
│       └── src/main/java/com/adobe/lakehouse/bronze/
│           ├── model/
│           │   ├── ConversionEvent.java   # Input event POJO
│           │   └── BronzeRecord.java      # Output record POJO
│           ├── function/
│           │   └── DeduplicationFunction.java  # Keyed state dedup
│           └── job/
│               └── BronzeIngestionJob.java     # Main job class
├── spark/
│   ├── conf/
│   │   └── spark-defaults.conf        # Spark configuration for Iceberg/S3
│   └── jobs/
│       ├── silver_etl.py              # Bronze → Silver transformations
│       └── gold_etl.py                # Silver → Gold aggregations
├── airflow/                           # (Coming soon) DAG definitions
└── notebooks/                         # (Coming soon) Jupyter analysis
```

## Data Engineering Standards

### 1. Exactly-Once Semantics
```
Flink: Checkpoints + RocksDB state backend + Kafka offsets
Spark: Iceberg ACID transactions + Idempotent writes
```

### 2. Late Arriving Data
```
Flink: Watermark = event_time - 5 minutes
       Allowed lateness = 1 hour
       Late events flagged (is_late_arrival = true)
```

### 3. Deduplication
```
Bronze (Flink): Keyed state by event_id, 24-hour TTL
Silver (Spark): MERGE INTO with event_id as key
```

### 4. Error Handling
```
Dead Letter Queue: roman-numeral-events-dlq (Kafka topic)
Error metadata: original_payload, error_type, error_message, failed_at
```

### 5. Backfill Strategy
```
Spark: Parameterized date ranges
       Write to staging table, validate, then swap
       Maintain audit trail in _audit columns
```

### 6. Failure Recovery
```
Flink: Auto-restart (3 attempts, 30s delay)
       Checkpoint-based state recovery
       Savepoints for planned upgrades
       
Spark: Airflow retry policy
       Idempotent batch writes (safe to re-run)
```

## Running Jobs

### Flink Bronze Streaming (Real-time)

```bash
# Build the Flink job JAR (from host)
cd data-platform/flink/bronze-ingestion
mvn clean package -DskipTests

# Connect to Flink JobManager
docker exec -it flink-jobmanager bash

# Submit the streaming job
flink run \
  -c com.adobe.lakehouse.bronze.job.BronzeIngestionJob \
  /opt/flink-jobs/bronze-ingestion/target/bronze-ingestion-1.0.0.jar

# View running jobs
flink list

# Cancel a job
flink cancel <job-id>

# Create savepoint (for upgrades)
flink savepoint <job-id> s3://lakehouse/flink/savepoints
```

### Spark Silver/Gold Batch (via Airflow)

```bash
# Manual execution for testing
docker exec -it spark-master bash

# Run Silver ETL
spark-submit \
  --master spark://spark-master:7077 \
  --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.4.2,\
org.apache.hadoop:hadoop-aws:3.3.4 \
  /opt/spark-jobs/silver_etl.py \
  --date 2024-12-25

# Run Gold ETL
spark-submit \
  --master spark://spark-master:7077 \
  /opt/spark-jobs/gold_etl.py \
  --date 2024-12-25
```

## Medallion Architecture Layers

| Layer | Engine | Purpose | Update Frequency |
|-------|--------|---------|------------------|
| **Bronze** | Flink | Raw event landing | Real-time (streaming) |
| **Silver** | Spark | Cleaned, validated, SCD Type 2 | Hourly (batch) |
| **Gold** | Spark | Star schema, aggregates | Daily (batch) |

### Bronze Layer (Flink)
- Append-only raw events from Kafka
- Partitioned by year/month/day
- Late arrivals flagged
- No transformations

### Silver Layer (Spark)
- Deduplicated and validated
- SCD Type 2 for `dim_users`
- Entity-focused schema
- Incremental updates

### Gold Layer (Spark)
- Star schema for BI
- Pre-aggregated metrics
- Optimized for analytics queries
- Daily refresh

## Monitoring

### Flink UI
- **URL**: http://localhost:8092
- **Metrics**: Jobs, tasks, checkpoints, backpressure
- **Alerts**: Failed checkpoints, task failures

### Spark UI
- **Master**: http://localhost:8090
- **Worker**: http://localhost:8091
- **Metrics**: Applications, stages, executors

### Kafka Monitoring

```bash
# List topics
docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# View Bronze ingestion consumer lag
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe --group flink-bronze-ingestion

# View Dead Letter Queue
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic roman-numeral-events-dlq \
  --from-beginning
```

## Troubleshooting

### Flink Job Fails to Start
```bash
# Check JobManager logs
docker logs flink-jobmanager

# Verify Kafka connectivity
docker exec flink-taskmanager nc -zv kafka 9092

# Check MinIO (S3) for checkpoint access
docker exec flink-jobmanager curl http://minio:9000
```

### Checkpoint Failures
```bash
# Verify MinIO bucket exists
docker exec minio mc ls myminio/lakehouse/flink/checkpoints

# Check Flink configuration
docker exec flink-jobmanager cat /opt/flink/conf/flink-conf.yaml
```

### Spark Job Fails
```bash
# Check Spark Master logs
docker logs spark-master

# Verify Hive Metastore connectivity
docker exec spark-master nc -zv hive-metastore 9083

# Check Iceberg table
docker exec -it spark-master spark-sql \
  --conf spark.sql.catalog.lakehouse=org.apache.iceberg.spark.SparkCatalog \
  -e "SHOW TABLES IN lakehouse.bronze"
```

## Configuration Files

### Flink (`flink/conf/flink-conf.yaml`)
- Checkpoint interval: 60 seconds
- State backend: RocksDB (incremental)
- Restart strategy: 3 attempts, 30s delay
- S3 endpoint: MinIO

### Spark (`spark/conf/spark-defaults.conf`)
- Iceberg catalog: Hive Metastore
- S3 endpoint: MinIO
- File format: Parquet with Snappy
