"""
Gold Layer ELT: Silver → Gold
==============================
Aggregates Silver data into analytics-ready Gold tables.

## Why ELT (not ETL)?
---------------------
Data is first Loaded to Bronze (raw), then Transformed in-place within
the lakehouse. This is the modern Medallion architecture pattern.

Tables Created:
- gold.daily_conversion_summary: Daily metrics
- gold.user_metrics: Per-user analytics
- gold.popular_numbers: Most popular conversions

Usage (with Airflow interval dates):
    spark-submit gold_elt.py \
        --interval-start '2025-01-01T00:00:00' \
        --interval-end '2025-01-02T00:00:00'

Usage (full load - no arguments):
    spark-submit gold_elt.py
"""

import argparse

from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, count, sum as spark_sum, avg, max as spark_max, min as spark_min,
    when, current_timestamp, date_format, lit
)


def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description="Gold ELT Job")
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
    """Create Spark session with Iceberg support."""
    return (SparkSession.builder
        .appName("GoldELT")
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


def create_gold_tables(spark):
    """Create Gold tables if they don't exist."""
    spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.gold")
    
    # Drop existing tables to ensure schema matches
    spark.sql("DROP TABLE IF EXISTS lakehouse.gold.daily_conversion_summary")
    spark.sql("DROP TABLE IF EXISTS lakehouse.gold.user_metrics")
    spark.sql("DROP TABLE IF EXISTS lakehouse.gold.popular_numbers")
    
    # Daily Summary
    spark.sql("""
        CREATE TABLE lakehouse.gold.daily_conversion_summary (
            event_date STRING,
            total_conversions LONG,
            total_numbers_converted LONG,
            single_conversions LONG,
            range_conversions LONG,
            max_number_converted INT,
            min_number_converted INT,
            avg_response_time_ns DOUBLE,
            generated_at TIMESTAMP
        )
        USING iceberg
    """)
    print("✅ Table gold.daily_conversion_summary ready")
    
    # User Metrics
    spark.sql("""
        CREATE TABLE lakehouse.gold.user_metrics (
            user_id LONG,
            total_requests LONG,
            total_numbers LONG,
            avg_input_value DOUBLE,
            last_activity TIMESTAMP,
            generated_at TIMESTAMP
        )
        USING iceberg
    """)
    print("✅ Table gold.user_metrics ready")
    
    # Popular Numbers
    spark.sql("""
        CREATE TABLE lakehouse.gold.popular_numbers (
            input_number INT,
            output_roman STRING,
            request_count LONG,
            generated_at TIMESTAMP
        )
        USING iceberg
    """)
    print("✅ Table gold.popular_numbers ready")


def build_daily_summary(spark, interval_start=None, interval_end=None):
    """
    Build daily conversion summary using interval-based processing.
    
    Args:
        spark: SparkSession
        interval_start: Start of processing window (from Airflow)
        interval_end: End of processing window (from Airflow)
    """
    print("\n📊 Building daily_conversion_summary...")
    
    # Read Silver
    silver_df = spark.table("lakehouse.silver.fact_conversions")
    
    if interval_start and interval_end:
        print(f"   Interval mode: {interval_start} → {interval_end}")
        # Find dates affected by Silver records processed in this interval
        affected_dates = (silver_df
            .filter(
                (col("processed_at") >= lit(interval_start)) &
                (col("processed_at") < lit(interval_end))
            )
            .select(date_format("event_time", "yyyy-MM-dd").alias("event_date"))
            .distinct()
            .collect())
        
        if not affected_dates:
            print("   ⚠️ No records processed in this interval")
            try:
                return spark.table("lakehouse.gold.daily_conversion_summary").count()
            except Exception:
                return 0
        
        date_list = [row["event_date"] for row in affected_dates]
        print(f"   Affected dates: {date_list}")
        
        # Recalculate only affected dates from FULL Silver data
        silver_df = silver_df.filter(
            date_format("event_time", "yyyy-MM-dd").isin(date_list)
        )
    else:
        print("   Full load mode: Processing all Silver records")
    
    record_count = silver_df.count()
    if record_count == 0:
        print("   ⚠️ No records to aggregate")
        return 0
    
    daily_df = (silver_df
        .withColumn("event_date", date_format("event_time", "yyyy-MM-dd"))
        .groupBy("event_date")
        .agg(
            count("*").alias("total_conversions"),
            spark_sum("conversion_count").alias("total_numbers_converted"),
            count(when(col("event_type") == "SINGLE", 1)).alias("single_conversions"),
            count(when(col("event_type") == "RANGE", 1)).alias("range_conversions"),
            spark_max("input_number").alias("max_number_converted"),
            spark_min("input_number").alias("min_number_converted"),
            avg("response_time_nanos").alias("avg_response_time_ns")
        )
        .withColumn("generated_at", current_timestamp())
    )
    
    # MERGE to upsert affected dates
    daily_df.createOrReplaceTempView("new_daily_data")
    
    spark.sql("""
        MERGE INTO lakehouse.gold.daily_conversion_summary target
        USING new_daily_data source
        ON target.event_date = source.event_date
        WHEN MATCHED THEN UPDATE SET *
        WHEN NOT MATCHED THEN INSERT *
    """)
    
    final_count = spark.table("lakehouse.gold.daily_conversion_summary").count()
    print(f"   ✅ daily_conversion_summary: {final_count} days")
    return final_count


def build_user_metrics(spark, interval_start=None, interval_end=None):
    """
    Build user metrics using interval-based processing.
    
    Args:
        spark: SparkSession
        interval_start: Start of processing window (from Airflow)
        interval_end: End of processing window (from Airflow)
    """
    print("\n📊 Building user_metrics...")
    
    silver_df = spark.table("lakehouse.silver.fact_conversions")
    
    if interval_start and interval_end:
        print(f"   Interval mode: {interval_start} → {interval_end}")
        # Find users affected by Silver records processed in this interval
        affected_users = (silver_df
            .filter(
                (col("processed_at") >= lit(interval_start)) &
                (col("processed_at") < lit(interval_end))
            )
            .filter(col("user_id").isNotNull())
            .select("user_id")
            .distinct()
            .collect())
        
        if not affected_users:
            print("   ⚠️ No user activity in this interval")
            try:
                return spark.table("lakehouse.gold.user_metrics").count()
            except Exception:
                return 0
        
        user_list = [row["user_id"] for row in affected_users]
        print(f"   Affected users: {len(user_list)}")
        
        # Recalculate only affected users from FULL Silver data
        silver_df = silver_df.filter(col("user_id").isin(user_list))
    else:
        print("   Full load mode: Processing all Silver records")
    
    user_df = (silver_df
        .filter(col("user_id").isNotNull())
        .groupBy("user_id")
        .agg(
            count("*").alias("total_requests"),
            spark_sum("conversion_count").alias("total_numbers"),
            avg("input_number").alias("avg_input_value"),
            spark_max("event_time").alias("last_activity")
        )
        .withColumn("generated_at", current_timestamp())
    )
    
    if user_df.count() > 0:
        user_df.createOrReplaceTempView("new_user_data")
        
        spark.sql("""
            MERGE INTO lakehouse.gold.user_metrics target
            USING new_user_data source
            ON target.user_id = source.user_id
            WHEN MATCHED THEN UPDATE SET *
            WHEN NOT MATCHED THEN INSERT *
        """)
        
        final_count = spark.table("lakehouse.gold.user_metrics").count()
        print(f"   ✅ user_metrics: {final_count} users")
        return final_count
    else:
        print("   ⚠️ No authenticated users (all anonymous)")
        return 0


def build_popular_numbers(spark):
    """Build popular numbers ranking (full recalculation - aggregation table)."""
    print("\n📊 Building popular_numbers...")
    
    # For aggregation tables like "top N", we recalculate fully
    # as the ranking can change with any new data
    silver_df = spark.table("lakehouse.silver.fact_conversions")
    
    popular_df = (silver_df
        .filter(col("input_number").isNotNull())
        .groupBy("input_number", "output_roman")
        .agg(count("*").alias("request_count"))
        .orderBy(col("request_count").desc())
        .limit(100)  # Top 100
        .withColumn("generated_at", current_timestamp())
    )
    
    # For ranking tables, overwrite is appropriate (ranking changes)
    popular_df.createOrReplaceTempView("new_popular_data")
    
    # Clear and insert (simpler than MERGE for ranking tables)
    spark.sql("DELETE FROM lakehouse.gold.popular_numbers WHERE 1=1")
    spark.sql("""
        INSERT INTO lakehouse.gold.popular_numbers
        SELECT * FROM new_popular_data
    """)
    
    final_count = spark.table("lakehouse.gold.popular_numbers").count()
    print(f"   ✅ popular_numbers: {final_count} numbers")
    spark.table("lakehouse.gold.popular_numbers").show(10, truncate=False)
    return final_count


def main():
    """Main entry point."""
    args = parse_args()
    
    print("=" * 60)
    print("Gold Layer ELT: Silver → Gold")
    print("=" * 60)
    
    if args.interval_start and args.interval_end:
        print(f"Interval: {args.interval_start} → {args.interval_end}")
    else:
        print("Mode: Full load (no interval specified)")
    
    spark = create_spark_session()
    spark.sparkContext.setLogLevel("WARN")
    
    # Ensure tables exist
    create_gold_tables(spark)
    
    # Build aggregates with interval dates
    daily_count = build_daily_summary(
        spark,
        interval_start=args.interval_start,
        interval_end=args.interval_end
    )
    user_count = build_user_metrics(
        spark,
        interval_start=args.interval_start,
        interval_end=args.interval_end
    )
    popular_count = build_popular_numbers(spark)  # Always full recalc for rankings
    
    print("\n" + "=" * 60)
    print("✅ Gold ELT Complete!")
    print(f"   daily_conversion_summary: {daily_count} records")
    print(f"   user_metrics: {user_count} records")
    print(f"   popular_numbers: {popular_count} records")
    print("=" * 60)
    
    spark.stop()


if __name__ == "__main__":
    main()

