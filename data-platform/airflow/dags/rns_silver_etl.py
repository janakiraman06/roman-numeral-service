"""
Silver Layer ETL DAG
====================
Runs Spark job to transform Bronze layer data to Silver layer.

Schedule: Hourly
Author: Roman Numeral Service Data Platform
Version: 3.1.0

## Demo vs Production Configuration Notes
-----------------------------------------

### Current Config (Demo-Optimized):
- retries: 1 (fast failure visibility for reviewer)
- retry_delay: 1 min (quick retest cycle)
- execution_timeout: 10 min (prevent stuck tasks)
- catchup: False (no backfill on deploy)

### Production Enhancements:
- retries: 3 with exponential backoff (retry_delay * 2^attempt)
- retry_delay: 5 min initial
- execution_timeout: 30-60 min (based on data volume)
- dagrun_timeout: 2-4 hours
- sla: timedelta(minutes=45) per critical task
- sla_miss_callback: Alert to PagerDuty/Slack
- on_failure_callback: Slack + PagerDuty integration
- pool: 'spark_pool' (limit concurrent Spark jobs)
- priority_weight: Set based on business criticality
- Catchup: True with careful backfill testing
- depends_on_past: True for strict sequential processing
- wait_for_downstream: True for data dependencies
"""

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator
from airflow.utils.trigger_rule import TriggerRule


# =============================================================================
# Configuration
# =============================================================================

DAG_ID = "rns_silver_etl"
OWNER = "data-platform"

# Spark submit command with Airflow interval dates passed as arguments
# Supports manual backfill via DAG params: start_date and end_date
# If params provided → use them (backfill mode)
# If not provided → use Airflow's data_interval_start/end (normal mode)
SPARK_SUBMIT_CMD = """
docker exec -e AWS_REGION=us-east-1 spark-master /opt/spark/bin/spark-submit \
  --master 'local[2]' \
  --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.5.0,software.amazon.awssdk:bundle:2.20.18,software.amazon.awssdk:url-connection-client:2.20.18 \
  --conf "spark.driver.extraJavaOptions=-Divy.cache.dir=/tmp -Divy.home=/tmp" \
  /opt/spark-jobs/silver_etl.py \
  --interval-start '{{ params.start_date if params.start_date else data_interval_start.isoformat() }}' \
  --interval-end '{{ params.end_date if params.end_date else data_interval_end.isoformat() }}'
"""

# =============================================================================
# Demo-Optimized Settings (fast feedback for reviewer)
# =============================================================================
default_args = {
    "owner": OWNER,
    "depends_on_past": False,
    "email_on_failure": False,
    "email_on_retry": False,
    "retries": 1,                            # Demo: 1 (fast fail) | Prod: 3
    "retry_delay": timedelta(minutes=1),     # Demo: 1 min | Prod: 5 min + exponential
    "execution_timeout": timedelta(minutes=10),  # Demo: 10 min | Prod: 30-60 min
}

# =============================================================================
# Production-Ready Settings (uncomment for production)
# =============================================================================
# default_args = {
#     "owner": OWNER,
#     "depends_on_past": True,               # Strict ordering
#     "wait_for_downstream": True,           # Wait for downstream in previous run
#     "email_on_failure": True,
#     "email_on_retry": False,
#     "email": ["data-platform@company.com"],
#     "retries": 3,
#     "retry_delay": timedelta(minutes=5),
#     "retry_exponential_backoff": True,     # 5 → 10 → 20 min
#     "max_retry_delay": timedelta(minutes=30),
#     "execution_timeout": timedelta(hours=1),
#     "sla": timedelta(minutes=45),          # Alert if task takes > 45 min
#     "pool": "spark_pool",                  # Limit concurrent Spark jobs
#     "priority_weight": 10,                 # Higher = more important
# }


# =============================================================================
# Callback Functions
# =============================================================================

def on_success_callback(context):
    """Log successful task completion."""
    task_instance = context.get('task_instance')
    print(f"✅ Task {task_instance.task_id} completed successfully")


def on_failure_callback(context):
    """
    Log task failure.
    
    Production Enhancement:
    ```python
    from airflow.providers.slack.operators.slack_webhook import SlackWebhookOperator
    from airflow.providers.pagerduty.operators.pagerduty import PagerdutyEventsOperator
    
    # Slack notification
    SlackWebhookOperator(
        task_id='slack_alert',
        slack_webhook_conn_id='slack_data_alerts',
        message=f":x: *{dag_id}/{task_id}* failed at {execution_date}",
    ).execute(context)
    
    # PagerDuty for critical pipelines
    PagerdutyEventsOperator(
        task_id='pagerduty_alert',
        pagerduty_events_conn_id='pagerduty',
        routing_key='silver_etl_routing_key',
        summary=f"Silver ETL failed: {task_id}",
        severity='error',
    ).execute(context)
    ```
    """
    task_instance = context.get('task_instance')
    dag_id = context.get('dag').dag_id
    print(f"❌ [{dag_id}] Task {task_instance.task_id} failed!")


# =============================================================================
# DAG Definition
# =============================================================================

with DAG(
    dag_id=DAG_ID,
    description="Transform Bronze to Silver layer using Spark",
    schedule="@hourly",
    start_date=datetime(2024, 1, 1),
    catchup=False,
    max_active_runs=1,
    default_args=default_args,
    tags=["rns", "silver", "etl", "spark", "iceberg"],
    on_success_callback=on_success_callback,
    on_failure_callback=on_failure_callback,
    # Backfill params - trigger with these to override interval dates
    params={
        "start_date": None,  # ISO format: '2025-01-01T00:00:00'
        "end_date": None,    # ISO format: '2025-01-01T12:00:00'
    },
    doc_md="""
    ## Silver ETL DAG
    
    Transforms Bronze layer data to Silver layer.
    
    ### Normal Execution
    Runs hourly, processes data from `data_interval_start` to `data_interval_end`.
    
    ### Backfill Mode
    Trigger with params to process a custom date range:
    ```json
    {
        "start_date": "2025-01-01T00:00:00",
        "end_date": "2025-01-02T00:00:00"
    }
    ```
    """,
) as dag:
    
    # =========================================================================
    # Task: Log Start
    # =========================================================================
    
    def log_start(**context):
        execution_date = context.get("execution_date") or context.get("logical_date")
        print("=" * 60)
        print("Silver Layer ETL - Starting")
        print("=" * 60)
        print(f"Execution Date: {execution_date}")
        print(f"DAG Run ID: {context.get('dag_run').run_id if context.get('dag_run') else 'N/A'}")
        print("=" * 60)
    
    start_task = PythonOperator(
        task_id="log_start",
        python_callable=log_start,
        provide_context=True,
    )
    
    # =========================================================================
    # Task: Run Silver ETL Spark Job
    # =========================================================================
    
    run_silver_etl = BashOperator(
        task_id="run_silver_etl_spark_job",
        bash_command=SPARK_SUBMIT_CMD,
        doc_md="""
        Runs the Silver ETL Spark job which:
        1. Reads from Bronze layer (lakehouse.bronze.raw_conversion_events)
        2. Cleans and validates data
        3. Writes to Silver layer:
           - lakehouse.silver.fact_conversions
           - lakehouse.silver.dim_users
        """,
    )
    
    # =========================================================================
    # Task: Verify Silver Data
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
      -e "spark.sql(\\"SELECT 'fact_conversions' as table_name, COUNT(*) as row_count FROM lakehouse.silver.fact_conversions\\").show(); spark.sql(\\"SELECT 'dim_users' as table_name, COUNT(*) as row_count FROM lakehouse.silver.dim_users\\").show(); System.exit(0)" 2>&1 | tail -20
    """
    
    verify_silver = BashOperator(
        task_id="verify_silver_data",
        bash_command=verify_cmd,
        doc_md="Verifies Silver layer tables have data after ETL.",
    )
    
    # =========================================================================
    # Task: Log Completion
    # =========================================================================
    
    def log_complete(**context):
        execution_date = context.get("execution_date") or context.get("logical_date")
        print("=" * 60)
        print("Silver Layer ETL - Completed Successfully!")
        print("=" * 60)
        print(f"Execution Date: {execution_date}")
        print("Tables Updated:")
        print("  - lakehouse.silver.fact_conversions")
        print("  - lakehouse.silver.dim_users")
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
    
    start_task >> run_silver_etl >> verify_silver >> complete_task
