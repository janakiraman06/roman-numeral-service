# ADR-012: Unified Lakehouse Architecture

**Status**: Accepted  
**Date**: 2024-12-25  
**Deciders**: Engineering Team  
**Technical Story**: Implement production-grade data engineering platform

## Context and Problem Statement

The Roman Numeral Service generates valuable operational data (API requests, conversions, user behavior) that needs to be stored, processed, and analyzed for:

1. **Operational Analytics** - Understanding API usage patterns
2. **Business Intelligence** - Reporting on service adoption and performance
3. **Data Science** - Building models for capacity planning and anomaly detection
4. **Audit/Compliance** - Maintaining historical records of all conversions

We need a modern data architecture that supports both real-time streaming and batch processing, while enabling ACID transactions and time-travel queries.

## Decision Drivers

1. **Unified Architecture** - Single platform for streaming and batch
2. **Production Parity** - Local setup mirrors cloud deployment (AWS/GCP/Azure)
3. **Open Standards** - Avoid vendor lock-in
4. **Cost Efficiency** - Leverage commodity object storage
5. **Time Travel** - Query historical data states
6. **Schema Evolution** - Handle schema changes gracefully
7. **Data Quality** - Built-in validation and lineage tracking

## Considered Options

### Option A: Traditional Data Warehouse (Lambda Architecture)
```
                    ┌──────────────────────┐
                    │   Lambda Architecture │
                    └──────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │  Batch   │   │  Speed   │   │ Serving  │
        │  Layer   │   │  Layer   │   │  Layer   │
        └──────────┘   └──────────┘   └──────────┘
              │               │               │
              ▼               ▼               ▼
        Hadoop/Hive     Kafka/Storm    Cassandra/Redis
```

**Pros**:
- Well-understood pattern
- Clear separation of concerns

**Cons**:
- Duplicate logic (batch and speed layers)
- Complex operational burden
- Inconsistent results between layers
- Higher maintenance cost

### Option B: Kappa Architecture
```
        ┌─────────────────────────────────────┐
        │        Kappa Architecture           │
        └─────────────────────────────────────┘
                         │
                         ▼
        ┌─────────────────────────────────────┐
        │         Kafka (Immutable Log)       │
        └─────────────────────────────────────┘
                         │
                         ▼
        ┌─────────────────────────────────────┐
        │    Stream Processing (Flink/Spark)  │
        └─────────────────────────────────────┘
                         │
                         ▼
        ┌─────────────────────────────────────┐
        │         Serving Layer               │
        └─────────────────────────────────────┘
```

**Pros**:
- Single processing pipeline
- Simpler logic (no duplicate code)
- True real-time processing

**Cons**:
- Reprocessing requires replaying all events
- Expensive for historical analysis
- Limited query flexibility

### Option C: Unified Lakehouse (Netflix-Style) ✅

```
        ┌─────────────────────────────────────────────────────────┐
        │                Unified Lakehouse Architecture           │
        └─────────────────────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        │                           │                           │
        ▼                           ▼                           ▼
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│    Ingest     │          │   Process     │          │    Serve      │
│    Layer      │          │   Layer       │          │    Layer      │
└───────────────┘          └───────────────┘          └───────────────┘
        │                           │                           │
        ▼                           ▼                           ▼
   ┌─────────┐              ┌─────────────┐            ┌─────────────┐
   │  Kafka  │──────────────│    Spark    │────────────│   Jupyter   │
   └─────────┘              └─────────────┘            └─────────────┘
        │                           │                           │
        └───────────────────────────┼───────────────────────────┘
                                    │
                                    ▼
        ┌─────────────────────────────────────────────────────────┐
        │            Apache Iceberg (Table Format)                │
        │  ┌─────────────────────────────────────────────────────┐│
        │  │              Medallion Architecture                 ││
        │  │  ┌─────────┐    ┌─────────┐    ┌─────────┐         ││
        │  │  │ Bronze  │───▶│ Silver  │───▶│  Gold   │         ││
        │  │  │  (Raw)  │    │(Cleaned)│    │  (BI)   │         ││
        │  │  └─────────┘    └─────────┘    └─────────┘         ││
        │  └─────────────────────────────────────────────────────┘│
        └─────────────────────────────────────────────────────────┘
                                    │
                                    ▼
        ┌─────────────────────────────────────────────────────────┐
        │              MinIO (S3-Compatible Storage)              │
        └─────────────────────────────────────────────────────────┘
```

**Pros**:
- Single source of truth (Iceberg tables)
- Unified batch and streaming
- ACID transactions on object storage
- Time-travel queries
- Schema evolution
- Open format (no vendor lock-in)
- Netflix, Apple, LinkedIn use this pattern

**Cons**:
- More components to manage
- Requires understanding of modern data stack

## Decision

We chose **Option C: Unified Lakehouse Architecture** because:

1. **Industry Standard**: Used by Netflix, Airbnb, Apple, LinkedIn
2. **Cloud Parity**: MinIO locally → S3 in production (same APIs)
3. **Open Table Format**: Iceberg is the emerging standard (vs. Delta, Hudi)
4. **Unified Processing**: Spark handles both streaming and batch
5. **Cost Efficiency**: Cheap object storage with warehouse capabilities

## Architecture Components

### Storage Layer
| Component | Local | AWS | GCP | Azure |
|-----------|-------|-----|-----|-------|
| Object Storage | MinIO | S3 | GCS | ADLS |
| Table Format | Iceberg | Iceberg | Iceberg | Iceberg |

### Catalog Layer
| Component | Local | AWS | GCP | Azure |
|-----------|-------|-----|-----|-------|
| Metastore | Iceberg REST Catalog | Glue Catalog | BigQuery | Unity Catalog |

### Processing Layer
| Component | Local | Cloud |
|-----------|-------|-------|
| Batch | Spark (Docker) | EMR/Dataproc/Synapse |
| Streaming | Spark Streaming | Same |
| Orchestration | Airflow | MWAA/Cloud Composer |

### Serving Layer
| Component | Purpose |
|-----------|---------|
| Jupyter | Ad-hoc analysis & BI |
| PySpark | SQL queries on Iceberg |

## Medallion Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      Medallion Architecture Layers                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐       │
│  │   BRONZE    │         │   SILVER    │         │    GOLD     │       │
│  │  (Raw Data) │         │  (Cleaned)  │         │    (BI)     │       │
│  └─────────────┘         └─────────────┘         └─────────────┘       │
│        │                       │                       │               │
│        ▼                       ▼                       ▼               │
│  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐       │
│  │ Raw events  │         │ Deduplicated│         │ Star Schema │       │
│  │ from Kafka  │   ───▶  │ validated   │   ───▶  │ aggregates  │       │
│  │ as-is       │         │ SCD Type 2  │         │ for BI      │       │
│  └─────────────┘         └─────────────┘         └─────────────┘       │
│                                                                         │
│  Location:                Location:                Location:            │
│  s3://lakehouse-bronze    s3://lakehouse-silver    s3://lakehouse-gold │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Bronze Layer (Raw)
- **Purpose**: Immutable landing zone for raw events
- **Schema**: Matches Kafka event schema exactly
- **Processing**: Append-only, no transformations
- **Retention**: Long-term (regulatory compliance)

### Silver Layer (Cleaned)
- **Purpose**: Cleansed, deduplicated, validated data
- **Schema**: Entity-focused (3NF-ish)
- **Processing**: 
  - Deduplication by event_id
  - Schema validation
  - SCD Type 2 for dimensions (users)
  - Late-arriving data handling
- **Retention**: Medium-term

### Gold Layer (BI)
- **Purpose**: Business-ready aggregates
- **Schema**: Star schema for analytics
- **Processing**:
  - Pre-aggregated metrics
  - Dimensional modeling
  - Materialized views
- **Retention**: As needed for dashboards

## Consequences

### Positive
- **Time Travel**: Query any historical state
- **Schema Evolution**: Add/remove columns safely
- **ACID Transactions**: Consistent reads during writes
- **Unified Stack**: Same tools for streaming and batch
- **Cloud Portable**: Easy migration to AWS/GCP/Azure

### Negative
- **Learning Curve**: Team needs to understand Iceberg
- **Local Resources**: Docker setup requires ~8GB RAM
- **Complexity**: More components than simple database

### Risks
- **Metastore Availability**: Single point of failure
- **Data Skew**: Large files may cause processing issues
- **Compaction**: Need to manage small file problem

## Implementation Details

### Docker Services
```yaml
services:
  minio:          # S3-compatible storage
  hive-metastore: # Iceberg catalog
  spark:          # Processing engine (Chunk 10)
  airflow:        # Orchestration (Chunk 11)
```

### Bucket Structure
```
s3://lakehouse-bronze/
  └── conversion_events/
      └── year=2024/month=12/day=25/
          └── *.parquet

s3://lakehouse-silver/
  ├── fact_conversions/
  └── dim_users/

s3://lakehouse-gold/
  ├── daily_conversion_metrics/
  └── user_activity_summary/
```

### Table Naming Convention
```
<layer>.<domain>_<entity>_<type>

Examples:
- bronze.conversion_events_raw
- silver.fact_conversions
- silver.dim_users
- gold.daily_conversion_metrics
```

## Related ADRs

- [ADR-011: Event-Driven Architecture](011-event-driven-architecture.md) - Kafka event streaming
- ADR-013: Data Quality with Great Expectations (planned)
- ADR-014: Data Lineage with Marquez (planned)

## References

- [Netflix Data Mesh Architecture](https://netflixtechblog.com/data-mesh-a-data-movement-and-processing-platform-netflix-1288bcab2873)
- [Apache Iceberg Documentation](https://iceberg.apache.org/)
- [Medallion Architecture (Databricks)](https://www.databricks.com/glossary/medallion-architecture)
- [Building a Lakehouse (Tabular)](https://www.tabular.io/blog/)

