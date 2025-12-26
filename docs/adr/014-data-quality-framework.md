# ADR-014: Data Quality Framework Selection

## Status

Accepted

## Date

2024-01-15

## Context

The Roman Numeral Service data platform requires a data quality framework to:
- Validate data at each Medallion layer (Bronze, Silver, Gold)
- Ensure SCD Type 2 integrity in dimension tables
- Detect schema drift and data anomalies
- Provide audit trails and documentation
- Integrate with Airflow orchestration

Two leading open-source frameworks were considered:
1. **Great Expectations** - Python-native data quality framework
2. **AWS Deequ** - Scala/Spark-native data quality library

## Decision Drivers

1. **Cloud Portability**: Platform uses MinIO (not AWS S3), Hive Metastore (not Glue)
2. **Language Consistency**: Airflow DAGs are Python; avoid adding Scala
3. **Operational Simplicity**: Easy to configure, deploy, and maintain
4. **Airflow Integration**: First-class support for orchestration
5. **Documentation**: Built-in reporting for audits and debugging
6. **Scale**: Demo/assessment scale, not TB+ production volumes

## Considered Options

### Option 1: Great Expectations (Python)

**Pros:**
- Python-native, consistent with Airflow DAGs
- Cloud-agnostic (works with any storage backend)
- JSON-based expectations (easy to version control)
- Built-in Data Docs (HTML reports auto-generated)
- Official Airflow provider package
- Large community and extensive documentation
- Supports Pandas, Spark, and SQL backends

**Cons:**
- Slightly slower than native Spark for large datasets
- Less sophisticated constraint suggestion than Deequ
- Requires separate Python environment in Spark jobs

### Option 2: AWS Deequ (Scala/Spark)

**Pros:**
- Spark-native (runs in same JVM as Spark jobs)
- Excellent performance for large datasets
- Powerful constraint suggestion from profiling
- Anomaly detection on metrics over time
- Used by Netflix, Amazon internally
- Tight integration with AWS Glue Data Quality

**Cons:**
- Scala-based (adds new language to stack)
- AWS-centric design philosophy
- Requires custom visualization for results
- No official Airflow provider
- Steeper learning curve

### Comparison Matrix

| Criteria | Great Expectations | AWS Deequ |
|----------|-------------------|-----------|
| Language | Python | Scala |
| Cloud Lock-in | None | AWS-leaning |
| Airflow Integration | Native provider | Custom operator |
| Learning Curve | Low | Medium-High |
| Data Docs | Built-in HTML | Custom needed |
| Spark Performance | Good | Excellent |
| Constraint Suggestion | Basic | Advanced |
| Community Size | Larger | Smaller |
| Config Format | JSON/YAML | Scala code |

## Decision

**Use Great Expectations** for data quality validation.

## Rationale

1. **Cloud-Agnostic Architecture**: The platform explicitly chose MinIO over S3 and Hive over Glue to maintain portability. Great Expectations aligns with this philosophy.

2. **Language Consistency**: 
   - Spring Boot service: Java
   - Flink streaming: Java
   - Airflow DAGs: Python
   - Adding Scala would increase complexity without proportional benefit.

3. **Operational Simplicity**: JSON expectation suites are:
   - Easy to review in pull requests
   - Simple to version control
   - Readable by non-developers (data analysts, QA)

4. **Built-in Documentation**: Data Docs automatically generate HTML reports showing:
   - Which expectations passed/failed
   - Sample failing rows
   - Historical trends
   - This is valuable for debugging and audits.

5. **Appropriate for Scale**: At demo/assessment scale, Deequ's performance advantages don't materialize. Great Expectations is sufficient.

## Consequences

### Positive

- Single language (Python) for data engineering tooling
- Easy onboarding for new team members
- Self-documenting validation results
- Seamless Airflow integration via TaskFlow API
- Portable to any cloud or on-premises environment

### Negative

- May need to revisit if data volumes grow to TB+ scale
- Constraint suggestion requires manual rule definition
- Slightly more overhead than native Spark validation

### Risks

- **Performance at Scale**: If data volumes grow significantly, may need to:
  - Optimize expectation suites
  - Run validations on sampled data
  - Consider Deequ for specific high-volume tables

- **Spark Context Management**: When running GE with SparkDFExecutionEngine, need to manage Spark sessions carefully to avoid conflicts with ETL jobs.

## Implementation

### Directory Structure

```
data-platform/great_expectations/
├── great_expectations.yml           # Main configuration
├── expectations/
│   ├── bronze_conversion_events_suite.json
│   ├── silver_fact_conversions_suite.json
│   └── silver_dim_users_suite.json
├── checkpoints/
│   ├── bronze_validation_checkpoint.yml
│   └── silver_validation_checkpoint.yml
├── plugins/
│   └── ge_airflow_operator.py       # Custom Airflow operator
└── uncommitted/
    └── config_variables.yml         # Environment-specific config
```

### Airflow Integration

```python
@task(task_id="run_great_expectations")
def run_great_expectations(**context) -> dict:
    """Run GE checkpoint after Silver ETL."""
    # Uses silver_validation_checkpoint
    # Validates fact_conversions and dim_users
    ...
```

### Key Expectation Types Used

| Layer | Expectation | Purpose |
|-------|-------------|---------|
| Bronze | `expect_column_values_to_match_regex` | UUID format validation |
| Silver | `expect_column_values_to_be_unique` | Deduplication verification |
| Silver | `expect_compound_columns_to_be_unique` | SCD Type 2 integrity |
| Silver | `expect_column_pair_values_A_to_be_greater_than_B` | valid_to >= valid_from |

## References

- [Great Expectations Documentation](https://docs.greatexpectations.io/)
- [AWS Deequ GitHub](https://github.com/awslabs/deequ)
- [Choosing a Data Quality Tool](https://www.dataengineeringweekly.com/p/data-quality-tools-comparison)
- [Netflix Data Quality at Scale](https://netflixtechblog.com/data-quality-at-netflix-b0d9f3a8a5a0)

## Future Considerations

1. **Deequ for Specific Tables**: If certain tables grow to TB+ scale, consider Deequ for those specific validations while keeping GE for the rest.

2. **Great Expectations Cloud**: Evaluate GE Cloud for centralized expectation management and collaboration features.

3. **Real-time Validation**: For streaming Bronze layer, consider implementing lightweight validation in Flink rather than batch GE validation.

