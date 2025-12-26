"""
Apache Superset Configuration
==============================
Configuration for Roman Numeral Service BI dashboards.

This file is mounted into the Superset container at /app/pythonpath/superset_config.py

See: https://superset.apache.org/docs/installation/configuring-superset

Author: Roman Numeral Service Data Platform
Version: 1.0.0
"""

import os
from datetime import timedelta

# =============================================================================
# Superset Core Settings
# =============================================================================

# Flask secret key for session signing
SECRET_KEY = os.environ.get("SUPERSET_SECRET_KEY", "rns_superset_secret_key_change_in_prod")

# Superset application name
APP_NAME = "Roman Numeral Analytics"

# =============================================================================
# Database Configuration
# =============================================================================

# Superset metadata database
SQLALCHEMY_DATABASE_URI = os.environ.get(
    "DATABASE_URL",
    "postgresql+psycopg2://superset:superset_secret@superset-db:5432/superset"
)

# =============================================================================
# Cache Configuration
# =============================================================================

CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 300,
    "CACHE_KEY_PREFIX": "superset_",
    "CACHE_REDIS_URL": os.environ.get("REDIS_URL", "redis://superset-redis:6379/0"),
}

DATA_CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 86400,  # 24 hours
    "CACHE_KEY_PREFIX": "superset_data_",
    "CACHE_REDIS_URL": os.environ.get("REDIS_URL", "redis://superset-redis:6379/0"),
}

FILTER_STATE_CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 86400,
    "CACHE_KEY_PREFIX": "superset_filter_",
    "CACHE_REDIS_URL": os.environ.get("REDIS_URL", "redis://superset-redis:6379/0"),
}

EXPLORE_FORM_DATA_CACHE_CONFIG = {
    "CACHE_TYPE": "RedisCache",
    "CACHE_DEFAULT_TIMEOUT": 86400,
    "CACHE_KEY_PREFIX": "superset_explore_",
    "CACHE_REDIS_URL": os.environ.get("REDIS_URL", "redis://superset-redis:6379/0"),
}

# =============================================================================
# Feature Flags
# =============================================================================

FEATURE_FLAGS = {
    # Enable dashboard embedding
    "EMBEDDABLE_CHARTS": True,
    "EMBEDDED_SUPERSET": True,
    # Enable SQL Lab features
    "ENABLE_TEMPLATE_PROCESSING": True,
    # Enable advanced analytics
    "ALERT_REPORTS": False,  # Requires additional setup
    # Dashboard features
    "DASHBOARD_NATIVE_FILTERS": True,
    "DASHBOARD_CROSS_FILTERS": True,
    "DASHBOARD_NATIVE_FILTERS_SET": True,
    # Other useful features
    "ESCAPE_MARKDOWN_HTML": True,
    "ENABLE_EXPLORE_DRAG_AND_DROP": True,
}

# =============================================================================
# Security Configuration
# =============================================================================

# Allow embedding in iframes (for development)
SESSION_COOKIE_SAMESITE = "Lax"
SESSION_COOKIE_SECURE = False  # Set to True in production with HTTPS
ENABLE_CORS = True
CORS_OPTIONS = {
    "origins": ["*"],
    "supports_credentials": True,
}

# Public role permissions (for read-only dashboards)
PUBLIC_ROLE_LIKE = "Gamma"

# CSRF protection
WTF_CSRF_ENABLED = True
WTF_CSRF_TIME_LIMIT = 60 * 60 * 24 * 7  # 1 week

# =============================================================================
# SQL Lab Configuration
# =============================================================================

# Enable SQL Lab
ENABLE_SQLLAB = True

# Query limits
SQL_MAX_ROW = 100000
DISPLAY_MAX_ROW = 10000

# Query timeout (seconds)
SQLLAB_TIMEOUT = 300

# Async query mode (requires Celery for production)
SQLLAB_ASYNC_TIME_LIMIT_SEC = 60 * 60  # 1 hour

# =============================================================================
# Visualization Settings
# =============================================================================

# Default visualization
VIZ_TYPE_DENYLIST = []

# Row limit for charts
ROW_LIMIT = 50000

# MapBox API key (for map visualizations)
MAPBOX_API_KEY = os.environ.get("MAPBOX_API_KEY", "")

# =============================================================================
# Logging Configuration
# =============================================================================

LOG_FORMAT = "%(asctime)s:%(levelname)s:%(name)s:%(message)s"
LOG_LEVEL = "INFO"

# =============================================================================
# Celery Configuration (for async queries in production)
# =============================================================================

# Celery is optional - only needed for async queries and alerts/reports
# For production, configure a proper Celery broker

# class CeleryConfig:
#     broker_url = os.environ.get("CELERY_BROKER_URL", "redis://superset-redis:6379/1")
#     result_backend = os.environ.get("CELERY_RESULT_BACKEND", "redis://superset-redis:6379/2")
#     imports = ["superset.sql_lab"]
#
# CELERY_CONFIG = CeleryConfig

# =============================================================================
# Database Connection Templates
# =============================================================================

# Pre-configured database connections can be added here
# These will appear in the "Add Database" dropdown

PREFERRED_DATABASES = [
    "PostgreSQL",
    "Trino",
    "Apache Hive",
    "Apache Spark SQL",
]

# =============================================================================
# Default Database Connections
# =============================================================================

# Note: Database connections are typically added via the UI
# However, you can bootstrap them programmatically using Superset's API
# or by importing a saved configuration

# Example SQL for connecting to the OLTP PostgreSQL:
# postgresql+psycopg2://romannumeral:romannumeral_secret@postgres:5432/romannumeral

# Example connection string for Hive/Spark SQL (requires additional driver):
# hive://hive-metastore:9083/lakehouse

# Example connection string for Trino (if added):
# trino://trino:8080/iceberg/lakehouse

