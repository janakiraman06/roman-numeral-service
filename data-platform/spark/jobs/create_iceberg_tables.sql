-- ============================================
-- Iceberg Table Creation for Roman Numeral Lakehouse
-- ============================================
-- Run this via: spark-sql -f create_iceberg_tables.sql
-- Or in Spark shell: spark.sql("...")
-- ============================================

-- Create database/namespace
CREATE DATABASE IF NOT EXISTS bronze;
CREATE DATABASE IF NOT EXISTS silver;
CREATE DATABASE IF NOT EXISTS gold;

-- ============================================
-- BRONZE LAYER: Raw events from Kafka
-- ============================================
CREATE TABLE IF NOT EXISTS bronze.conversion_events (
    event_id STRING,
    event_time TIMESTAMP,
    event_type STRING,
    user_id BIGINT,
    api_key_prefix STRING,
    client_ip STRING,
    correlation_id STRING,
    input_number INT,
    output_roman STRING,
    range_min INT,
    range_max INT,
    result_count INT,
    page_offset INT,
    page_limit INT,
    response_time_nanos BIGINT,
    status STRING,
    ingested_at TIMESTAMP,
    kafka_partition INT,
    kafka_offset BIGINT
)
USING iceberg
PARTITIONED BY (days(event_time))
TBLPROPERTIES (
    'write.format.default' = 'parquet',
    'write.parquet.compression-codec' = 'zstd'
);

-- ============================================
-- SILVER LAYER: Cleaned and deduplicated
-- ============================================
CREATE TABLE IF NOT EXISTS silver.fact_conversions (
    event_id STRING,
    event_time TIMESTAMP,
    user_id BIGINT,
    client_ip STRING,
    event_type STRING,
    input_number INT,
    output_roman STRING,
    range_min INT,
    range_max INT,
    result_count INT,
    response_time_ms DOUBLE,
    status STRING,
    processed_at TIMESTAMP
)
USING iceberg
PARTITIONED BY (days(event_time))
TBLPROPERTIES (
    'write.format.default' = 'parquet',
    'write.parquet.compression-codec' = 'zstd'
);

-- Dimension table for users (SCD Type 2)
CREATE TABLE IF NOT EXISTS silver.dim_users (
    user_id BIGINT,
    first_seen_at TIMESTAMP,
    last_activity_at TIMESTAMP,
    total_requests BIGINT,
    last_client_ip STRING,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    is_current BOOLEAN,
    version INT
)
USING iceberg
TBLPROPERTIES (
    'write.format.default' = 'parquet'
);

-- ============================================
-- GOLD LAYER: Aggregated analytics
-- ============================================
CREATE TABLE IF NOT EXISTS gold.daily_conversion_stats (
    date_key DATE,
    total_conversions BIGINT,
    single_conversions BIGINT,
    range_conversions BIGINT,
    unique_users BIGINT,
    avg_response_time_ms DOUBLE,
    p50_response_time_ms DOUBLE,
    p95_response_time_ms DOUBLE,
    p99_response_time_ms DOUBLE,
    error_count BIGINT,
    error_rate DOUBLE
)
USING iceberg
PARTITIONED BY (date_key)
TBLPROPERTIES (
    'write.format.default' = 'parquet'
);

CREATE TABLE IF NOT EXISTS gold.number_popularity (
    input_number INT,
    conversion_count BIGINT,
    last_converted_at TIMESTAMP
)
USING iceberg
TBLPROPERTIES (
    'write.format.default' = 'parquet'
);

CREATE TABLE IF NOT EXISTS gold.hourly_metrics (
    hour_key TIMESTAMP,
    total_requests BIGINT,
    success_count BIGINT,
    error_count BIGINT,
    avg_response_time_ms DOUBLE,
    max_response_time_ms DOUBLE
)
USING iceberg
PARTITIONED BY (days(hour_key))
TBLPROPERTIES (
    'write.format.default' = 'parquet'
);

