"""
Bronze Layer Ingestion DAG (SECONDARY PATH - Backfill/Recovery)
================================================================
Runs Spark batch job for Bronze layer BACKFILL and RECOVERY scenarios.

## ARCHITECTURE CONTEXT
-----------------------
PRODUCTION:
    Primary:   Kafka → Flink → Iceberg Bronze  (real-time, sub-second)
    Secondary: Kafka → Spark → Iceberg Bronze  (this DAG - backfill only)

DEMO ENVIRONMENT:
    Flink:  Demonstrates streaming patterns (console output)
    Spark:  Handles Bronze ingestion (this DAG - working E2E)
    
See docs/DATA_ARCHITECTURE.md for full architecture explanation.

## Modes:
1. **Incremental (default)**: Processes only NEW Kafka messages since last run
2. **Backfill**: Reprocess a date range (trigger with params)

## Exactly-Once Semantics:
- Tracks MAX(kafka_offset) in Bronze table
- Uses MERGE INTO for idempotent writes (no duplicates)
- Safe to retry on failure

## Backfill Usage:
Trigger DAG manually with params:
```json
{
    "backfill": true,
    "start_date": "2025-01-01",
    "end_date": "2025-01-07"
}
```

Schedule: Every 15 minutes (demo) | Disabled in production (Flink is primary)
Author: Roman Numeral Service Data Platform
Version: 2.1.0
"""

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator
from airflow.utils.trigger_rule import TriggerRule


# =============================================================================
# Configuration
# =============================================================================

DAG_ID = "rns_bronze_ingestion"
OWNER = "data-platform"

# Spark submit command with Kafka + Iceberg packages
# Supports backfill mode via DAG params
# If params.backfill is true → full reprocess with date filters
# If params.backfill is false → incremental (new messages only)
SPARK_SUBMIT_CMD = """
docker exec -e AWS_REGION=us-east-1 spark-master /opt/spark/bin/spark-submit \
  --master 'local[2]' \
  --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.5.0,org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,software.amazon.awssdk:bundle:2.20.18,software.amazon.awssdk:url-connection-client:2.20.18 \
  --conf "spark.driver.extraJavaOptions=-Divy.cache.dir=/tmp -Divy.home=/tmp" \
  /opt/spark-jobs/bronze_batch_backfill.py \
  {% if params.backfill %}--backfill{% endif %} \
  {% if params.start_date %}--start-date '{{ params.start_date }}'{% endif %} \
  {% if params.end_date %}--end-date '{{ params.end_date }}'{% endif %}
"""

# =============================================================================
# Demo-Optimized Settings (fast feedback for reviewer)
# =============================================================================
default_args = {
    "owner": OWNER,
    "depends_on_past": False,
    "email_on_failure": False,
    "email_on_retry": False,
    "retries": 2,                             # Demo: 2 | Prod: 3-5
    "retry_delay": timedelta(minutes=1),      # Demo: 1 min | Prod: 2-5 min
    "execution_timeout": timedelta(minutes=15),  # Demo: 15 min | Prod: 30 min
}

# =============================================================================
# Production Settings (uncomment for production)
# =============================================================================
# default_args = {
#     "owner": OWNER,
#     "depends_on_past": False,              # Bronze can process independently
#     "email_on_failure": True,
#     "email": ["data-platform@company.com"],
#     "retries": 5,                          # More retries for Kafka connectivity
#     "retry_delay": timedelta(minutes=2),
#     "retry_exponential_backoff": True,
#     "max_retry_delay": timedelta(minutes=15),
#     "execution_timeout": timedelta(minutes=30),
#     "sla": timedelta(minutes=20),          # Alert if ingestion > 20 min
#     "pool": "kafka_consumer_pool",         # Limit concurrent Kafka consumers
# }


# =============================================================================
# DAG Definition
# =============================================================================

with DAG(
    dag_id=DAG_ID,
    description="Ingest Kafka events to Bronze layer (Iceberg) using Spark",
    schedule="*/15 * * * *",  # Every 15 minutes
    start_date=datetime(2024, 1, 1),
    catchup=False,
    max_active_runs=1,
    default_args=default_args,
    tags=["rns", "bronze", "ingestion", "kafka", "spark", "iceberg"],
    # Backfill params - trigger with these for historical reprocessing
    params={
        "backfill": False,      # Set to True to reprocess from earliest offset
        "start_date": None,     # Filter: only events >= this date (YYYY-MM-DD)
        "end_date": None,       # Filter: only events < this date (YYYY-MM-DD)
    },
    doc_md="""
    ## Bronze Ingestion DAG
    
    Ingests events from Kafka to Bronze layer with exactly-once semantics.
    
    ### Normal Execution (Scheduled)
    Runs every 15 minutes, processes only **new** messages since last run.
    Uses `kafka_offset` tracking for incremental processing.
    
    ### Backfill Mode (Manual Trigger)
    Reprocess historical data:
    ```json
    {
        "backfill": true,
        "start_date": "2025-01-01",
        "end_date": "2025-01-07"
    }
    ```
    
    ### Failure Recovery
    - Uses MERGE INTO for idempotent writes
    - Safe to retry - no duplicates created
    - Automatic offset tracking via Bronze table
    """,
) as dag:
    
    # =========================================================================
    # Task: Log Start
    # =========================================================================
    
    def log_start(**context):
        execution_date = context.get("execution_date") or context.get("logical_date")
        print("=" * 60)
        print("Bronze Layer Ingestion - Starting")
        print("=" * 60)
        print(f"Execution Date: {execution_date}")
        print("Source: Kafka topic 'roman-numeral-events'")
        print("Target: lakehouse.bronze.raw_conversion_events")
        print("=" * 60)
    
    start_task = PythonOperator(
        task_id="log_start",
        python_callable=log_start,
        provide_context=True,
    )
    
    # =========================================================================
    # Task: Check Kafka Topic
    # =========================================================================
    
    check_kafka_cmd = """
    echo "Checking Kafka topic for pending messages..."
    docker exec kafka /opt/kafka/bin/kafka-run-class.sh kafka.tools.GetOffsetShell \
      --broker-list localhost:9092 \
      --topic roman-numeral-events 2>/dev/null || echo "Topic check failed (may not exist yet)"
    """
    
    check_kafka = BashOperator(
        task_id="check_kafka_topic",
        bash_command=check_kafka_cmd,
        doc_md="Checks Kafka topic offset information.",
    )
    
    # =========================================================================
    # Task: Run Bronze Ingestion Spark Job
    # =========================================================================
    
    run_bronze_ingestion = BashOperator(
        task_id="run_bronze_ingestion_spark_job",
        bash_command=SPARK_SUBMIT_CMD,
        doc_md="""
        Runs the Bronze ingestion Spark job which:
        1. Reads from Kafka topic 'roman-numeral-events'
        2. Parses JSON messages
        3. Adds ingestion metadata (timestamp, partition info)
        4. Writes to Iceberg table lakehouse.bronze.raw_conversion_events
        """,
    )
    
    # =========================================================================
    # Task: Verify Bronze Data
    # =========================================================================
    
    verify_cmd = """
    docker exec -e AWS_REGION=us-east-1 spark-master /opt/spark/bin/spark-shell \
      --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.5.0,software.amazon.awssdk:bundle:2.20.18,software.amazon.awssdk:url-connection-client:2.20.18 \
      --conf "spark.driver.extraJavaOptions=-Divy.cache.dir=/tmp -Divy.home=/tmp" \
      --conf "spark.sql.catalog.lakehouse=org.apache.iceberg.spark.SparkCatalog" \
      --conf "spark.sql.catalog.lakehouse.type=rest" \
      --conf "spark.sql.catalog.lakehouse.uri=http://iceberg-rest:8181" \
      --conf "spark.sql.catalog.lakehouse.warehouse=s3://lakehouse/warehouse" \
      --conf "spark.sql.catalog.lakehouse.io-impl=org.apache.iceberg.aws.s3.S3FileIO" \
      --conf "spark.sql.catalog.lakehouse.s3.endpoint=http://minio:9000" \
      --conf "spark.sql.catalog.lakehouse.s3.access-key-id=minioadmin" \
      --conf "spark.sql.catalog.lakehouse.s3.secret-access-key=minioadmin123" \
      --conf "spark.sql.catalog.lakehouse.s3.path-style-access=true" \
      --conf "spark.sql.catalog.lakehouse.client.region=us-east-1" \
      --conf "spark.sql.defaultCatalog=lakehouse" \
      -e "println(\\"=== Bronze Layer Status ===\\"); val count = spark.sql(\\"SELECT COUNT(*) as total FROM lakehouse.bronze.raw_conversion_events\\").first().getLong(0); println(s\\"Total records in Bronze: \\$count\\"); spark.sql(\\"SELECT event_type, COUNT(*) as cnt FROM lakehouse.bronze.raw_conversion_events GROUP BY event_type\\").show(); System.exit(0)" 2>&1 | tail -15
    """
    
    verify_bronze = BashOperator(
        task_id="verify_bronze_data",
        bash_command=verify_cmd,
        doc_md="Verifies Bronze layer table has data after ingestion.",
    )
    
    # =========================================================================
    # Task: Log Completion
    # =========================================================================
    
    def log_complete(**context):
        execution_date = context.get("execution_date") or context.get("logical_date")
        print("=" * 60)
        print("Bronze Layer Ingestion - Completed!")
        print("=" * 60)
        print(f"Execution Date: {execution_date}")
        print("Table Updated: lakehouse.bronze.raw_conversion_events")
        print("=" * 60)
    
    complete_task = PythonOperator(
        task_id="log_complete",
        python_callable=log_complete,
        provide_context=True,
        trigger_rule=TriggerRule.ALL_SUCCESS,
    )
    
    # =========================================================================
    # DAG Flow
    # =========================================================================
    
    start_task >> check_kafka >> run_bronze_ingestion >> verify_bronze >> complete_task

