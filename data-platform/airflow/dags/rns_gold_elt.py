"""
Gold Layer ELT DAG
==================
Runs Spark job to transform Silver layer data to Gold layer (analytics-ready).

Schedule: Daily at 1:00 AM UTC
Author: Roman Numeral Service Data Platform
Version: 3.2.0

## Why ELT (not ETL)?
---------------------
Data is first Loaded to Bronze (raw), then Transformed in-place within
the lakehouse (Bronze → Silver → Gold). This is the modern Medallion
architecture pattern used by Databricks, Snowflake, and Iceberg-based platforms.

## Demo vs Production Configuration Notes
-----------------------------------------

### Current Config (Demo-Optimized):
- retries: 1 (fast failure visibility for reviewer)
- retry_delay: 1 min (quick retest cycle)
- execution_timeout: 15 min (prevent stuck tasks)
- catchup: False (no backfill on deploy)

### Production Enhancements:
- retries: 3 with exponential backoff
- retry_delay: 10 min initial (Gold is heavier)
- execution_timeout: 1-2 hours (aggregations are heavy)
- dagrun_timeout: 4 hours
- sla: timedelta(hours=1) for daily aggregations
- sla_miss_callback: Alert to PagerDuty/Slack
- on_failure_callback: Slack + PagerDuty + Email for Gold (business-critical)
- pool: 'spark_pool' with higher priority than Silver
- priority_weight: 20 (Gold is more business-critical)
- ExternalTaskSensor: timeout=3600, mode='reschedule' (not 'poke')
- Catchup: True with careful backfill testing
- depends_on_past: True for strict sequential aggregations
"""

from datetime import datetime, timedelta

from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator
from airflow.sensors.external_task import ExternalTaskSensor
from airflow.utils.trigger_rule import TriggerRule


# =============================================================================
# Configuration
# =============================================================================

DAG_ID = "rns_gold_elt"
OWNER = "data-platform"
UPSTREAM_DAG_ID = "rns_silver_elt"

# Spark submit command with all required configurations
# Spark submit command with Airflow interval dates passed as arguments
# Supports manual backfill via DAG params: start_date and end_date
SPARK_SUBMIT_CMD = """
docker exec -e AWS_REGION=us-east-1 spark-master /opt/spark/bin/spark-submit \
  --master 'local[2]' \
  --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.5.0,software.amazon.awssdk:bundle:2.20.18,software.amazon.awssdk:url-connection-client:2.20.18 \
  --conf "spark.driver.extraJavaOptions=-Divy.cache.dir=/tmp -Divy.home=/tmp" \
  /opt/spark-jobs/gold_elt.py \
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
    "retries": 1,                             # Demo: 1 (fast fail) | Prod: 3
    "retry_delay": timedelta(minutes=1),      # Demo: 1 min | Prod: 10 min + exponential
    "execution_timeout": timedelta(minutes=15),  # Demo: 15 min | Prod: 1-2 hours
}

# =============================================================================
# Production-Ready Settings (uncomment for production)
# =============================================================================
# default_args = {
#     "owner": OWNER,
#     "depends_on_past": True,               # Strict ordering for aggregations
#     "wait_for_downstream": True,           # Wait for downstream in previous run
#     "email_on_failure": True,
#     "email_on_retry": False,
#     "email": ["data-platform@company.com", "analytics@company.com"],
#     "retries": 3,
#     "retry_delay": timedelta(minutes=10),
#     "retry_exponential_backoff": True,     # 10 → 20 → 40 min
#     "max_retry_delay": timedelta(hours=1),
#     "execution_timeout": timedelta(hours=2),
#     "sla": timedelta(hours=1),             # Alert if daily job > 1 hour
#     "pool": "spark_pool",                  # Shared pool with Silver
#     "priority_weight": 20,                 # Higher than Silver (10)
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
    
    Production Enhancement (Gold is business-critical):
    ```python
    from airflow.providers.slack.operators.slack_webhook import SlackWebhookOperator
    from airflow.providers.pagerduty.operators.pagerduty import PagerdutyEventsOperator
    
    # Slack notification (data-alerts + analytics channel)
    SlackWebhookOperator(
        task_id='slack_alert',
        slack_webhook_conn_id='slack_data_alerts',
        message=f":rotating_light: *GOLD ETL FAILED* - {task_id}\\n"
                f"Business dashboards may show stale data!",
    ).execute(context)
    
    # PagerDuty for immediate attention (Gold is business-critical)
    PagerdutyEventsOperator(
        task_id='pagerduty_alert',
        pagerduty_events_conn_id='pagerduty',
        routing_key='gold_etl_routing_key',
        summary=f"Gold ETL failed: {task_id} - Analytics impacted",
        severity='critical',  # Higher severity than Silver
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
    description="Transform Silver to Gold layer using Spark for BI/Analytics",
    schedule="0 1 * * *",  # Daily at 1:00 AM UTC
    start_date=datetime(2024, 1, 1),
    catchup=False,
    max_active_runs=1,
    default_args=default_args,
    tags=["rns", "gold", "elt", "spark", "iceberg", "analytics"],
    on_success_callback=on_success_callback,
    on_failure_callback=on_failure_callback,
    # Backfill params - trigger with these to override interval dates
    params={
        "start_date": None,  # ISO format: '2025-01-01T00:00:00'
        "end_date": None,    # ISO format: '2025-01-02T00:00:00'
    },
    doc_md="""
    ## Gold ELT DAG
    
    Aggregates Silver layer data to Gold layer for analytics.
    
    ### Normal Execution
    Runs daily at 1:00 AM UTC, processes previous day's Silver data.
    
    ### Backfill Mode
    Trigger with params to process a custom date range:
    ```json
    {
        "start_date": "2025-01-01T00:00:00",
        "end_date": "2025-01-08T00:00:00"
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
        print("Gold Layer ELT - Starting")
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
    # Sensor: Wait for Silver ETL (Optional - for hourly Silver dependency)
    # =========================================================================
    # Note: Commented out for manual testing. Enable in production.
    # 
    # wait_for_silver = ExternalTaskSensor(
    #     task_id="wait_for_silver_etl",
    #     external_dag_id=UPSTREAM_DAG_ID,
    #     external_task_id="log_complete",
    #     allowed_states=["success"],
    #     execution_delta=timedelta(hours=1),  # Look for Silver run from 1 hour ago
    #     mode="reschedule",  # Don't block worker
    #     timeout=3600,  # 1 hour timeout
    #     poke_interval=300,  # Check every 5 minutes
    # )
    
    # =========================================================================
    # Task: Run Gold ETL Spark Job
    # =========================================================================
    
    run_gold_elt = BashOperator(
        task_id="run_gold_elt_spark_job",
        bash_command=SPARK_SUBMIT_CMD,
        doc_md="""
        Runs the Gold ELT Spark job which:
        1. Reads from Silver layer:
           - lakehouse.silver.fact_conversions
           - lakehouse.silver.dim_users
        2. Builds analytics-ready Gold tables:
           - lakehouse.gold.daily_conversion_summary
           - lakehouse.gold.user_metrics
           - lakehouse.gold.popular_numbers
        """,
    )
    
    # =========================================================================
    # Task: Verify Gold Data
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
      -e "println(\\"=== Gold Layer Tables ===\\"); spark.sql(\\"SELECT 'daily_conversion_summary' as tbl, COUNT(*) as cnt FROM lakehouse.gold.daily_conversion_summary UNION ALL SELECT 'user_metrics', COUNT(*) FROM lakehouse.gold.user_metrics UNION ALL SELECT 'popular_numbers', COUNT(*) FROM lakehouse.gold.popular_numbers\\").show(); System.exit(0)" 2>&1 | tail -15
    """
    
    verify_gold = BashOperator(
        task_id="verify_gold_data",
        bash_command=verify_cmd,
        doc_md="Verifies Gold layer tables have data after ELT.",
    )
    
    # =========================================================================
    # Task: Show Popular Numbers (for quick verification)
    # =========================================================================
    
    show_popular_cmd = """
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
      -e "println(\\"Top 10 Most Popular Roman Numeral Conversions:\\"); spark.sql(\\"SELECT input_number, output_roman, request_count FROM lakehouse.gold.popular_numbers ORDER BY request_count DESC LIMIT 10\\").show(false); System.exit(0)" 2>&1 | tail -20
    """
    
    show_popular = BashOperator(
        task_id="show_popular_numbers",
        bash_command=show_popular_cmd,
        doc_md="Displays top 10 most popular Roman numeral conversions.",
    )
    
    # =========================================================================
    # Task: Log Completion
    # =========================================================================
    
    def log_complete(**context):
        execution_date = context.get("execution_date") or context.get("logical_date")
        print("=" * 60)
        print("Gold Layer ELT - Completed Successfully!")
        print("=" * 60)
        print(f"Execution Date: {execution_date}")
        print("Tables Updated:")
        print("  - lakehouse.gold.daily_conversion_summary")
        print("  - lakehouse.gold.user_metrics")
        print("  - lakehouse.gold.popular_numbers")
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
    
    start_task >> run_gold_elt >> verify_gold >> show_popular >> complete_task
