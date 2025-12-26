# ADR-015: Data Lineage with Marquez and OpenLineage

## Status

Accepted

## Date

2024-01-15

## Context

The Roman Numeral Service data platform needs data lineage capabilities to:
- Track data flow from source (API) to lakehouse layers
- Understand upstream/downstream dependencies
- Debug data quality issues by tracing data origins
- Support compliance and audit requirements
- Enable impact analysis for schema changes

## Decision Drivers

1. **Open Standards**: Prefer portable, vendor-neutral solutions
2. **Lightweight Deployment**: Minimize infrastructure complexity
3. **Spark/Airflow Integration**: Native support for existing stack
4. **Self-Hosted**: No cloud vendor lock-in
5. **Active Community**: Ongoing development and support

## Considered Options

### Option 1: Marquez + OpenLineage

**Pros:**
- OpenLineage is the emerging industry standard
- Lightweight (single Java service + PostgreSQL)
- Native Spark and Airflow integrations
- Simple REST API for custom events
- Linux Foundation project (LF AI & Data)
- Web UI included

**Cons:**
- Basic UI compared to DataHub
- No data discovery features
- Limited column-level lineage

### Option 2: DataHub (LinkedIn)

**Pros:**
- Rich, modern UI
- Full data catalog with search
- Column-level lineage
- GraphQL API
- Active development (Acryl backing)

**Cons:**
- Heavy infrastructure (Kafka, Elasticsearch, MySQL, etc.)
- 4GB+ memory requirements
- More complex operations
- Overkill for lineage-only needs

### Option 3: Apache Atlas

**Pros:**
- Apache project with governance features
- Classification and tagging
- Hadoop ecosystem integration

**Cons:**
- Complex setup (Kafka, Solr, HBase)
- Hadoop-centric design
- Dated UI
- Heavy operational burden

### Option 4: Amundsen (Lyft)

**Pros:**
- Great search/discovery
- Metadata from multiple sources

**Cons:**
- Lineage is secondary (via Atlas/DataHub)
- Not lineage-native

## Decision

**Use Marquez with OpenLineage** for data lineage tracking.

## Rationale

1. **OpenLineage Standard**: OpenLineage is becoming the industry standard for lineage metadata. Using it now provides:
   - Portability to other systems (DataHub, Atlan, etc.)
   - Future-proof architecture
   - Growing ecosystem of integrations

2. **Right-Sized for Project**: Marquez provides exactly what's needed:
   - Lineage visualization
   - Run tracking
   - Simple deployment
   - No excess features adding complexity

3. **Native Spark Integration**: The OpenLineage Spark listener automatically captures:
   - Table reads/writes
   - Job execution status
   - Input/output datasets
   - Run metadata

4. **Airflow Compatibility**: Airflow 2.7+ has built-in OpenLineage support via:
   - `apache-airflow-providers-openlineage`
   - Automatic task lineage extraction
   - Integration with SparkSubmitOperator

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Data Sources                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────────┐     ┌─────────────┐     ┌─────────────┐      │
│   │   Spring    │     │    Kafka    │     │   Flink     │      │
│   │   Boot API  │────▶│   Events    │────▶│   Bronze    │      │
│   └─────────────┘     └─────────────┘     └──────┬──────┘      │
│                                                   │             │
│                                            OpenLineage          │
│                                                   │             │
│   ┌─────────────┐     ┌─────────────┐     ┌──────▼──────┐      │
│   │   Airflow   │────▶│    Spark    │────▶│   Silver/   │      │
│   │   DAGs      │     │   Jobs      │     │   Gold      │      │
│   └──────┬──────┘     └──────┬──────┘     └─────────────┘      │
│          │                   │                                  │
│   OpenLineage          OpenLineage                              │
│          │                   │                                  │
│          └─────────┬─────────┘                                  │
│                    │                                            │
│            ┌───────▼───────┐                                    │
│            │    Marquez    │                                    │
│            │   (Lineage)   │                                    │
│            └───────┬───────┘                                    │
│                    │                                            │
│            ┌───────▼───────┐                                    │
│            │  Marquez Web  │                                    │
│            │     UI        │                                    │
│            └───────────────┘                                    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation

### Docker Compose Services

```yaml
marquez:
  image: marquezproject/marquez:0.47.0
  ports:
    - "5000:5000"  # API
  environment:
    # PostgreSQL backend config

marquez-web:
  image: marquezproject/marquez-web:0.47.0
  ports:
    - "3001:3000"  # Web UI
```

### Spark Configuration

```properties
# spark-defaults.conf
spark.extraListeners=io.openlineage.spark.agent.OpenLineageSparkListener
spark.openlineage.transport.type=http
spark.openlineage.transport.url=http://marquez:5000
spark.openlineage.namespace=rns-lakehouse
```

### Airflow Configuration

```bash
# Environment variables
OPENLINEAGE_URL=http://marquez:5000
OPENLINEAGE_NAMESPACE=rns-lakehouse
```

### Lineage Events Captured

| Source | Events Captured |
|--------|-----------------|
| Spark Jobs | Table reads/writes, job status, run metadata |
| Airflow Tasks | Task execution, dependencies, run status |
| Custom Events | Manual lineage via REST API |

## Consequences

### Positive

- **Visibility**: Complete data flow from API to Gold layer
- **Debugging**: Trace data quality issues to source
- **Impact Analysis**: Understand downstream effects of changes
- **Compliance**: Audit trail for data transformations
- **Standards-Based**: Portable OpenLineage format

### Negative

- **Additional Service**: One more container to manage
- **Learning Curve**: Team needs to understand lineage concepts
- **Limited Column Lineage**: Table-level lineage only (Spark limitation)

### Risks

- **Performance**: OpenLineage listener adds minimal overhead (~1-2%)
- **Data Volume**: Lineage metadata can grow; need retention policy

## Access Points

| Component | URL | Purpose |
|-----------|-----|---------|
| Marquez API | http://localhost:5000 | REST API for lineage events |
| Marquez Admin | http://localhost:5001 | Health checks, metrics |
| Marquez Web | http://localhost:3001 | Visualization UI |

## Future Considerations

1. **Column-Level Lineage**: When Spark OpenLineage supports it better, enable via:
   ```properties
   spark.openlineage.facets.columnLineage.enabled=true
   ```

2. **Flink Integration**: Add OpenLineage to Flink jobs for Bronze layer lineage.

3. **DataHub Migration**: If richer data catalog features are needed, OpenLineage events can be consumed by DataHub.

## References

- [OpenLineage Specification](https://openlineage.io/spec/)
- [Marquez Documentation](https://marquezproject.ai/docs/)
- [OpenLineage Spark Integration](https://openlineage.io/docs/integrations/spark/)
- [Airflow OpenLineage Provider](https://airflow.apache.org/docs/apache-airflow-providers-openlineage/stable/index.html)

