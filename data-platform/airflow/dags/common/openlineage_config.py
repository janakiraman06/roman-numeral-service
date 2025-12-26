"""
OpenLineage Configuration for Airflow DAGs
============================================
Configures OpenLineage integration to emit lineage events to Marquez.

OpenLineage provides:
- Automatic lineage extraction from Spark jobs
- Task-level lineage from Airflow DAGs
- Column-level lineage (with supported extractors)
- Run state tracking (START, COMPLETE, FAIL)

Configuration is done via environment variables in docker-compose:
- OPENLINEAGE_URL: http://marquez:5000
- OPENLINEAGE_NAMESPACE: rns-lakehouse

For Spark jobs, add these configs:
--conf spark.extraListeners=io.openlineage.spark.agent.OpenLineageSparkListener
--conf spark.openlineage.transport.url=http://marquez:5000

Author: Roman Numeral Service Data Platform
Version: 1.0.0
"""

import os
from typing import Optional

# OpenLineage configuration
OPENLINEAGE_URL = os.getenv("OPENLINEAGE_URL", "http://marquez:5000")
OPENLINEAGE_NAMESPACE = os.getenv("OPENLINEAGE_NAMESPACE", "rns-lakehouse")
OPENLINEAGE_API_KEY = os.getenv("OPENLINEAGE_API_KEY", None)


def get_openlineage_config() -> dict:
    """
    Get OpenLineage configuration for Airflow.
    
    Returns:
        dict: Configuration dictionary for OpenLineage
    """
    config = {
        "url": OPENLINEAGE_URL,
        "namespace": OPENLINEAGE_NAMESPACE,
    }
    
    if OPENLINEAGE_API_KEY:
        config["api_key"] = OPENLINEAGE_API_KEY
    
    return config


def get_spark_openlineage_conf() -> str:
    """
    Get Spark configuration string for OpenLineage integration.
    
    Returns:
        str: Spark configuration flags for SparkSubmitOperator
    """
    return (
        f"--conf spark.extraListeners=io.openlineage.spark.agent.OpenLineageSparkListener "
        f"--conf spark.openlineage.transport.type=http "
        f"--conf spark.openlineage.transport.url={OPENLINEAGE_URL} "
        f"--conf spark.openlineage.namespace={OPENLINEAGE_NAMESPACE}"
    )


def emit_custom_lineage_event(
    job_name: str,
    run_id: str,
    inputs: list[dict],
    outputs: list[dict],
    event_type: str = "COMPLETE",
) -> None:
    """
    Emit a custom OpenLineage event to Marquez.
    
    Use this for custom lineage not captured automatically.
    
    Args:
        job_name: Name of the job/task
        run_id: Unique run identifier
        inputs: List of input datasets
        outputs: List of output datasets
        event_type: Event type (START, COMPLETE, FAIL)
    
    Example:
        emit_custom_lineage_event(
            job_name="transform_fact_conversions",
            run_id=context["run_id"],
            inputs=[
                {"namespace": "rns-lakehouse", "name": "bronze.conversion_events_raw"}
            ],
            outputs=[
                {"namespace": "rns-lakehouse", "name": "silver.fact_conversions"}
            ],
        )
    """
    try:
        import requests
        from datetime import datetime
        import uuid
        
        event = {
            "eventType": event_type,
            "eventTime": datetime.utcnow().isoformat() + "Z",
            "run": {
                "runId": run_id or str(uuid.uuid4()),
            },
            "job": {
                "namespace": OPENLINEAGE_NAMESPACE,
                "name": job_name,
            },
            "inputs": [
                {
                    "namespace": inp.get("namespace", OPENLINEAGE_NAMESPACE),
                    "name": inp.get("name"),
                }
                for inp in inputs
            ],
            "outputs": [
                {
                    "namespace": out.get("namespace", OPENLINEAGE_NAMESPACE),
                    "name": out.get("name"),
                }
                for out in outputs
            ],
            "producer": "https://github.com/rns-data-platform",
            "schemaURL": "https://openlineage.io/spec/1-0-5/OpenLineage.json#/$defs/RunEvent",
        }
        
        response = requests.post(
            f"{OPENLINEAGE_URL}/api/v1/lineage",
            json=event,
            headers={"Content-Type": "application/json"},
            timeout=5,
        )
        response.raise_for_status()
        
    except Exception as e:
        # Log but don't fail the task if lineage emission fails
        print(f"Warning: Failed to emit OpenLineage event: {e}")


# Dataset definitions for lineage
BRONZE_DATASETS = {
    "conversion_events_raw": {
        "namespace": OPENLINEAGE_NAMESPACE,
        "name": "bronze.conversion_events_raw",
        "description": "Raw conversion events from Kafka via Flink",
    },
}

SILVER_DATASETS = {
    "fact_conversions": {
        "namespace": OPENLINEAGE_NAMESPACE,
        "name": "silver.fact_conversions",
        "description": "Deduplicated, enriched conversion events",
    },
    "dim_users": {
        "namespace": OPENLINEAGE_NAMESPACE,
        "name": "silver.dim_users",
        "description": "User dimension with SCD Type 2",
    },
}

GOLD_DATASETS = {
    "dim_date": {
        "namespace": OPENLINEAGE_NAMESPACE,
        "name": "gold.dim_date",
        "description": "Standard date dimension",
    },
    "dim_conversion_type": {
        "namespace": OPENLINEAGE_NAMESPACE,
        "name": "gold.dim_conversion_type",
        "description": "Conversion type dimension",
    },
    "fact_daily_conversion_metrics": {
        "namespace": OPENLINEAGE_NAMESPACE,
        "name": "gold.fact_daily_conversion_metrics",
        "description": "Daily aggregated conversion metrics",
    },
    "fact_user_activity_daily": {
        "namespace": OPENLINEAGE_NAMESPACE,
        "name": "gold.fact_user_activity_daily",
        "description": "Daily user activity summary",
    },
    "fact_number_popularity": {
        "namespace": OPENLINEAGE_NAMESPACE,
        "name": "gold.fact_number_popularity",
        "description": "Number popularity rankings",
    },
}

