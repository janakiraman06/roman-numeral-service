"""
Alert Callbacks for Production DAGs
====================================
Provides callback functions for DAG/task failure and SLA miss notifications.

In production, these would integrate with:
- Slack (via slack_sdk or airflow.providers.slack)
- PagerDuty (via airflow.providers.pagerduty)
- Email (via airflow.utils.email)
- Custom webhooks

For this demo, callbacks log to stdout. Replace with actual integrations.

Author: Roman Numeral Service Data Platform
Version: 1.0.0
"""

import logging
from datetime import datetime
from typing import Any

from airflow.models import TaskInstance

logger = logging.getLogger(__name__)


def slack_alert_on_failure(context: dict[str, Any]) -> None:
    """
    Sends a Slack alert when a task fails.
    
    In production, use:
    ```python
    from airflow.providers.slack.operators.slack_webhook import SlackWebhookOperator
    
    SlackWebhookOperator(
        task_id='slack_alert',
        slack_webhook_conn_id='slack_webhook',
        message=message,
    ).execute(context)
    ```
    
    Args:
        context: Airflow context dictionary containing task instance info
    """
    ti: TaskInstance = context.get('task_instance')
    dag_id = context.get('dag').dag_id
    task_id = ti.task_id
    execution_date = context.get('execution_date')
    log_url = ti.log_url
    exception = context.get('exception')
    
    message = f"""
:red_circle: *DAG Task Failed*
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
*DAG:* `{dag_id}`
*Task:* `{task_id}`
*Execution Date:* {execution_date}
*Exception:* {str(exception)[:200] if exception else 'Unknown'}
*Log URL:* {log_url}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    """
    
    logger.error(f"SLACK ALERT (stub): {message}")
    # In production: post to Slack webhook
    

def email_alert_on_failure(context: dict[str, Any]) -> None:
    """
    Sends an email alert when a task fails.
    
    In production, use:
    ```python
    from airflow.utils.email import send_email
    
    send_email(
        to=['data-platform@company.com'],
        subject=f'DAG {dag_id} - Task {task_id} Failed',
        html_content=html_content,
    )
    ```
    
    Args:
        context: Airflow context dictionary
    """
    ti: TaskInstance = context.get('task_instance')
    dag_id = context.get('dag').dag_id
    task_id = ti.task_id
    execution_date = context.get('execution_date')
    
    subject = f"[AIRFLOW ALERT] DAG {dag_id} - Task {task_id} Failed"
    body = f"""
    DAG: {dag_id}
    Task: {task_id}
    Execution Date: {execution_date}
    Log URL: {ti.log_url}
    """
    
    logger.error(f"EMAIL ALERT (stub): {subject}\n{body}")
    # In production: send_email(...)


def sla_miss_callback(
    dag,
    task_list: list,
    blocking_task_list: list,
    slas: list,
    blocking_tis: list,
) -> None:
    """
    Called when a task misses its SLA.
    
    SLA (Service Level Agreement) defines the expected completion time.
    Missing an SLA indicates the pipeline is running slower than expected.
    
    Args:
        dag: The DAG object
        task_list: List of tasks that missed their SLA
        blocking_task_list: List of tasks blocking the SLA
        slas: List of SLA objects
        blocking_tis: List of blocking task instances
    """
    dag_id = dag.dag_id
    task_ids = [t.task_id for t in task_list]
    
    message = f"""
:warning: *SLA Miss Detected*
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
*DAG:* `{dag_id}`
*Tasks:* {', '.join(task_ids)}
*Time:* {datetime.now().isoformat()}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
This indicates the pipeline is running slower than expected.
Check for:
- Increased data volume
- Resource contention
- Upstream delays
    """
    
    logger.warning(f"SLA MISS ALERT (stub): {message}")
    # In production: post to Slack or PagerDuty


def task_success_callback(context: dict[str, Any]) -> None:
    """
    Called when a task succeeds. Useful for metrics/logging.
    
    Args:
        context: Airflow context dictionary
    """
    ti: TaskInstance = context.get('task_instance')
    dag_id = context.get('dag').dag_id
    task_id = ti.task_id
    duration = ti.duration
    
    logger.info(
        f"Task succeeded: dag={dag_id}, task={task_id}, duration={duration:.2f}s"
    )
    # In production: push metrics to Prometheus/Datadog


def dag_success_callback(context: dict[str, Any]) -> None:
    """
    Called when the entire DAG succeeds.
    
    Args:
        context: Airflow context dictionary
    """
    dag_id = context.get('dag').dag_id
    execution_date = context.get('execution_date')
    
    logger.info(f"DAG completed successfully: dag={dag_id}, date={execution_date}")
    # In production: push metrics, update status dashboard

