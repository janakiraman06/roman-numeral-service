"""
Great Expectations Airflow Operator
====================================
Custom operator to run Great Expectations checkpoints from Airflow.

This provides a cleaner integration than using PythonOperator with inline code.

Usage in DAG:
    from ge_airflow_operator import GreatExpectationsOperator
    
    validate_task = GreatExpectationsOperator(
        task_id='validate_silver_data',
        checkpoint_name='silver_validation_checkpoint',
        batch_kwargs={'run_date': '{{ ds }}'},
    )

Author: Roman Numeral Service Data Platform
Version: 1.0.0
"""

import os
from typing import Any, Dict, Optional

from airflow.models import BaseOperator
from airflow.utils.decorators import apply_defaults


class GreatExpectationsOperator(BaseOperator):
    """
    Airflow operator to run Great Expectations checkpoints.
    
    Attributes:
        checkpoint_name: Name of the GE checkpoint to run
        data_context_root_dir: Path to GE project root
        batch_kwargs: Dynamic parameters for batch request
        fail_task_on_validation_failure: Whether to fail the task if validation fails
        return_json_dict: Whether to return validation results as dict (for XCom)
    """
    
    template_fields = ['batch_kwargs']
    
    @apply_defaults
    def __init__(
        self,
        checkpoint_name: str,
        data_context_root_dir: Optional[str] = None,
        batch_kwargs: Optional[Dict[str, Any]] = None,
        fail_task_on_validation_failure: bool = True,
        return_json_dict: bool = True,
        **kwargs,
    ):
        super().__init__(**kwargs)
        self.checkpoint_name = checkpoint_name
        self.data_context_root_dir = data_context_root_dir or os.environ.get(
            'GE_DATA_CONTEXT_ROOT_DIR',
            '/opt/great_expectations'
        )
        self.batch_kwargs = batch_kwargs or {}
        self.fail_task_on_validation_failure = fail_task_on_validation_failure
        self.return_json_dict = return_json_dict
    
    def execute(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """
        Execute the Great Expectations checkpoint.
        
        Args:
            context: Airflow context dictionary
            
        Returns:
            Validation result dictionary (if return_json_dict=True)
            
        Raises:
            AirflowException: If validation fails and fail_task_on_validation_failure=True
        """
        from airflow.exceptions import AirflowException
        
        try:
            import great_expectations as gx
            from great_expectations.checkpoint import Checkpoint
        except ImportError:
            raise AirflowException(
                "Great Expectations is not installed. "
                "Install with: pip install great_expectations"
            )
        
        self.log.info(f"Running checkpoint: {self.checkpoint_name}")
        self.log.info(f"Data context root: {self.data_context_root_dir}")
        self.log.info(f"Batch kwargs: {self.batch_kwargs}")
        
        # Get the Data Context
        try:
            data_context = gx.get_context(
                context_root_dir=self.data_context_root_dir
            )
        except Exception as e:
            raise AirflowException(
                f"Failed to load Great Expectations context: {e}"
            )
        
        # Run the checkpoint
        try:
            result = data_context.run_checkpoint(
                checkpoint_name=self.checkpoint_name,
                batch_identifiers=self.batch_kwargs,
            )
        except Exception as e:
            raise AirflowException(
                f"Failed to run checkpoint {self.checkpoint_name}: {e}"
            )
        
        # Log results
        success = result.success
        self.log.info(f"Checkpoint {self.checkpoint_name} success: {success}")
        
        # Extract validation statistics
        validation_stats = self._extract_validation_stats(result)
        self.log.info(f"Validation stats: {validation_stats}")
        
        # Handle failure
        if not success and self.fail_task_on_validation_failure:
            failed_expectations = self._get_failed_expectations(result)
            raise AirflowException(
                f"Checkpoint {self.checkpoint_name} failed validation. "
                f"Failed expectations: {failed_expectations}"
            )
        
        # Return results for XCom
        if self.return_json_dict:
            return {
                'success': success,
                'checkpoint_name': self.checkpoint_name,
                'run_id': str(result.run_id),
                'validation_stats': validation_stats,
            }
        
        return None
    
    def _extract_validation_stats(self, result) -> Dict[str, Any]:
        """Extract summary statistics from validation result."""
        stats = {
            'total_expectations': 0,
            'successful_expectations': 0,
            'failed_expectations': 0,
            'success_percent': 0.0,
        }
        
        try:
            for validation_result in result.run_results.values():
                results = validation_result.get('validation_result', {})
                statistics = results.get('statistics', {})
                
                stats['total_expectations'] += statistics.get(
                    'evaluated_expectations', 0
                )
                stats['successful_expectations'] += statistics.get(
                    'successful_expectations', 0
                )
                stats['failed_expectations'] += statistics.get(
                    'unsuccessful_expectations', 0
                )
            
            if stats['total_expectations'] > 0:
                stats['success_percent'] = round(
                    stats['successful_expectations'] / stats['total_expectations'] * 100,
                    2
                )
        except Exception as e:
            self.log.warning(f"Could not extract validation stats: {e}")
        
        return stats
    
    def _get_failed_expectations(self, result) -> list:
        """Extract list of failed expectation types."""
        failed = []
        
        try:
            for validation_result in result.run_results.values():
                results = validation_result.get('validation_result', {})
                for expectation_result in results.get('results', []):
                    if not expectation_result.get('success', True):
                        failed.append(
                            expectation_result.get('expectation_config', {}).get(
                                'expectation_type', 'unknown'
                            )
                        )
        except Exception as e:
            self.log.warning(f"Could not extract failed expectations: {e}")
        
        return failed


class GreatExpectationsSensor(BaseOperator):
    """
    Sensor that waits for Great Expectations validation to pass.
    
    Useful for gating downstream tasks on data quality.
    """
    
    template_fields = ['batch_kwargs']
    
    @apply_defaults
    def __init__(
        self,
        checkpoint_name: str,
        data_context_root_dir: Optional[str] = None,
        batch_kwargs: Optional[Dict[str, Any]] = None,
        poke_interval: int = 60,
        timeout: int = 3600,
        **kwargs,
    ):
        super().__init__(**kwargs)
        self.checkpoint_name = checkpoint_name
        self.data_context_root_dir = data_context_root_dir
        self.batch_kwargs = batch_kwargs or {}
        self.poke_interval = poke_interval
        self.timeout = timeout
    
    def poke(self, context: Dict[str, Any]) -> bool:
        """Check if validation passes."""
        operator = GreatExpectationsOperator(
            task_id=f"{self.task_id}_inner",
            checkpoint_name=self.checkpoint_name,
            data_context_root_dir=self.data_context_root_dir,
            batch_kwargs=self.batch_kwargs,
            fail_task_on_validation_failure=False,
        )
        
        result = operator.execute(context)
        return result.get('success', False)

