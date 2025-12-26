"""
Silver Layer ETL: Bronze → Silver
==================================
Transforms raw Bronze events into cleansed Silver tables.

Transformations:
- Deduplication by event_id (keep latest)
- Filter out error events for fact table
- Derive computed columns (conversion_count)
- SCD Type 2 for user dimension (placeholder)

Tables Created:
- silver.fact_conversions: Cleansed conversion facts
- silver.dim_users: User dimension (SCD Type 2)

Usage (with Airflow interval dates):
    spark-submit silver_etl.py \
        --interval-start '2025-01-01T00:00:00' \
        --interval-end '2025-01-01T01:00:00'

Usage (full load - no arguments):
    spark-submit silver_etl.py
"""

import argparse
from datetime import datetime

from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, when, row_number, current_timestamp, count, max as spark_max, lit
)
from pyspark.sql.window import Window


def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description="Silver ETL Job")
    parser.add_argument(
        "--interval-start",
        type=str,
        default=None,
        help="Start of processing interval (ISO format)"
    )
    parser.add_argument(
        "--interval-end", 
        type=str,
        default=None,
        help="End of processing interval (ISO format)"
    )
    return parser.parse_args()


def create_spark_session():
    """Create Spark session with Iceberg REST catalog support."""
    return (SparkSession.builder
        .appName("SilverETL")
        .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
        # Iceberg REST Catalog configuration
        .config("spark.sql.catalog.lakehouse", "org.apache.iceberg.spark.SparkCatalog")
        .config("spark.sql.catalog.lakehouse.type", "rest")
        .config("spark.sql.catalog.lakehouse.uri", "http://iceberg-rest:8181")
        .config("spark.sql.catalog.lakehouse.warehouse", "s3://lakehouse/warehouse")
        .config("spark.sql.catalog.lakehouse.io-impl", "org.apache.iceberg.aws.s3.S3FileIO")
        .config("spark.sql.catalog.lakehouse.s3.endpoint", "http://minio:9000")
        .config("spark.sql.catalog.lakehouse.s3.path-style-access", "true")
        .config("spark.sql.catalog.lakehouse.s3.access-key-id", "minioadmin")
        .config("spark.sql.catalog.lakehouse.s3.secret-access-key", "minioadmin123")
        .config("spark.sql.catalog.lakehouse.client.region", "us-east-1")
        # S3/MinIO configuration
        .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000")
        .config("spark.hadoop.fs.s3a.access.key", "minioadmin")
        .config("spark.hadoop.fs.s3a.secret.key", "minioadmin123")
        .config("spark.hadoop.fs.s3a.path.style.access", "true")
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
        .getOrCreate())


def create_silver_tables(spark):
    """Create Silver tables if they don't exist."""
    spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.silver")
    
    # Drop and recreate to fix schema
    spark.sql("DROP TABLE IF EXISTS lakehouse.silver.fact_conversions")
    
    # Fact Conversions table
    spark.sql("""
        CREATE TABLE lakehouse.silver.fact_conversions (
            event_id STRING,
            event_time TIMESTAMP,
            event_type STRING,
            user_id LONG,
            client_ip STRING,
            correlation_id STRING,
            input_number INT,
            output_roman STRING,
            range_min INT,
            range_max INT,
            result_count INT,
            response_time_nanos LONG,
            status STRING,
            conversion_count INT,
            processed_at TIMESTAMP
        )
        USING iceberg
        PARTITIONED BY (days(event_time))
    """)
    print("✅ Table silver.fact_conversions ready")
    
    # Dim Users table (SCD Type 2 placeholder)
    spark.sql("""
        CREATE TABLE IF NOT EXISTS lakehouse.silver.dim_users (
            user_id LONG,
            last_client_ip STRING,
            total_requests LONG,
            last_activity TIMESTAMP,
            valid_from TIMESTAMP,
            valid_to TIMESTAMP,
            is_current BOOLEAN,
            version INT
        )
        USING iceberg
    """)
    print("✅ Table silver.dim_users ready")


def transform_fact_conversions(spark, interval_start=None, interval_end=None):
    """
    Transform Bronze → Silver fact_conversions with interval-based processing.
    
    Args:
        spark: SparkSession
        interval_start: Start of processing window (from Airflow)
        interval_end: End of processing window (from Airflow)
    """
    print("\n📊 Transforming fact_conversions...")
    
    # Read Bronze with interval filter
    bronze_df = spark.table("lakehouse.bronze.raw_conversion_events")
    
    if interval_start and interval_end:
        print(f"   Interval mode: {interval_start} → {interval_end}")
        # Filter Bronze by ingested_at within the interval
        bronze_df = bronze_df.filter(
            (col("ingested_at") >= lit(interval_start)) &
            (col("ingested_at") < lit(interval_end))
        )
    else:
        print("   Full load mode: Processing all Bronze records")
    
    bronze_count = bronze_df.count()
    print(f"   Bronze records in interval: {bronze_count}")
    
    if bronze_count == 0:
        print("   ⚠️ No records in this interval")
        try:
            return spark.table("lakehouse.silver.fact_conversions").count()
        except Exception:
            return 0
    
    # Deduplicate by event_id (keep latest by ingested_at)
    window = Window.partitionBy("event_id").orderBy(col("ingested_at").desc())
    
    new_silver_df = (bronze_df
        .filter(col("status") == "SUCCESS")  # Filter errors
        .withColumn("row_num", row_number().over(window))
        .filter(col("row_num") == 1)
        .drop("row_num")
        # Derive conversion_count
        .withColumn("conversion_count",
            when(col("event_type") == "RANGE", col("range_max") - col("range_min") + 1)
            .otherwise(1))
        .withColumn("processed_at", current_timestamp())
        .select(
            "event_id", "event_time", "event_type", "user_id", "client_ip",
            "correlation_id", "input_number", "output_roman", "range_min",
            "range_max", "result_count", "response_time_nanos", "status",
            "conversion_count", "processed_at"
        )
    )
    
    new_count = new_silver_df.count()
    print(f"   New Silver records: {new_count}")
    
    if new_count == 0:
        print("   ⚠️ No valid records after filtering")
        try:
            return spark.table("lakehouse.silver.fact_conversions").count()
        except Exception:
            return 0
    
    # MERGE (upsert) - Insert new, update existing based on event_id
    new_silver_df.createOrReplaceTempView("new_silver_data")
    
    spark.sql("""
        MERGE INTO lakehouse.silver.fact_conversions target
        USING new_silver_data source
        ON target.event_id = source.event_id
        WHEN MATCHED THEN UPDATE SET *
        WHEN NOT MATCHED THEN INSERT *
    """)
    
    final_count = spark.table("lakehouse.silver.fact_conversions").count()
    print(f"   ✅ fact_conversions complete: {final_count} total records ({new_count} new/updated)")
    return final_count


def update_dim_users(spark):
    """Update Silver dim_users with latest user activity."""
    print("\n📊 Updating dim_users...")
    
    # Aggregate user metrics from fact table
    fact_df = spark.table("lakehouse.silver.fact_conversions")
    
    user_metrics = (fact_df
        .filter(col("user_id").isNotNull())
        .groupBy("user_id")
        .agg(
            spark_max("client_ip").alias("last_client_ip"),
            count("*").alias("total_requests"),
            spark_max("event_time").alias("last_activity")
        )
        .withColumn("valid_from", current_timestamp())
        .withColumn("valid_to", col("valid_from"))  # Same as valid_from for current
        .withColumn("is_current", col("user_id").isNotNull())  # Always true for new
        .withColumn("version", col("total_requests").cast("int") % 100 + 1)  # Simplified version
    )
    
    if user_metrics.count() > 0:
        user_metrics.writeTo("lakehouse.silver.dim_users").overwritePartitions()
        print(f"   ✅ dim_users updated: {user_metrics.count()} users")
    else:
        print("   ⚠️ No authenticated users found (all anonymous)")
    
    return user_metrics.count()


def main():
    """Main entry point."""
    args = parse_args()
    
    print("=" * 60)
    print("Silver Layer ETL: Bronze → Silver")
    print("=" * 60)
    
    if args.interval_start and args.interval_end:
        print(f"Interval: {args.interval_start} → {args.interval_end}")
    else:
        print("Mode: Full load (no interval specified)")
    
    spark = create_spark_session()
    spark.sparkContext.setLogLevel("WARN")
    
    # Ensure tables exist
    create_silver_tables(spark)
    
    # Transform fact table with interval dates
    fact_count = transform_fact_conversions(
        spark, 
        interval_start=args.interval_start,
        interval_end=args.interval_end
    )
    
    # Update user dimension
    dim_count = update_dim_users(spark)
    
    print("\n" + "=" * 60)
    print("✅ Silver ETL Complete!")
    print(f"   fact_conversions: {fact_count} records")
    print(f"   dim_users: {dim_count} records")
    print("=" * 60)
    
    spark.stop()


if __name__ == "__main__":
    main()

