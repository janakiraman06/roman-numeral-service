"""
Test Medallion Architecture: Bronze → Silver → Gold
====================================================
Demonstrates the complete data flow through all lakehouse layers.

This script:
1. Creates sample Bronze data (simulating Flink output)
2. Transforms to Silver layer (cleansed, deduplicated)
3. Aggregates to Gold layer (analytics-ready)

Usage:
    docker exec spark-master spark-submit \
        --packages org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.5.0,org.apache.hadoop:hadoop-aws:3.3.4 \
        /opt/spark-jobs/test_medallion_flow.py
"""

from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, count, avg, sum as spark_sum, max as spark_max, min as spark_min,
    current_timestamp, date_format, lit, when, row_number
)
from pyspark.sql.window import Window
from datetime import datetime
import uuid

def create_spark_session():
    """Create Spark session with Iceberg support using Hadoop catalog."""
    return (SparkSession.builder
        .appName("TestMedallionFlow")
        .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
        .config("spark.sql.catalog.lakehouse", "org.apache.iceberg.spark.SparkCatalog")
        .config("spark.sql.catalog.lakehouse.type", "hadoop")
        .config("spark.sql.catalog.lakehouse.warehouse", "s3a://lakehouse/warehouse")
        # Use HadoopFileIO instead of S3FileIO (compatible with AWS SDK v1)
        .config("spark.sql.catalog.lakehouse.io-impl", "org.apache.iceberg.hadoop.HadoopFileIO")
        .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000")
        .config("spark.hadoop.fs.s3a.access.key", "minioadmin")
        .config("spark.hadoop.fs.s3a.secret.key", "minioadmin123")
        .config("spark.hadoop.fs.s3a.path.style.access", "true")
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        .config("spark.hadoop.fs.s3a.connection.ssl.enabled", "false")
        .getOrCreate())


def create_sample_bronze_data(spark):
    """Create sample Bronze data simulating what Flink would produce."""
    print("\n" + "="*60)
    print("STEP 1: Creating Bronze Layer Data")
    print("="*60)
    
    # Sample conversion events (simulating Flink output)
    data = [
        (str(uuid.uuid4()), "2024-12-26 10:00:00", "single", "user1", "192.168.1.1", 42, "XLII", None, None, "SUCCESS"),
        (str(uuid.uuid4()), "2024-12-26 10:01:00", "single", "user2", "192.168.1.2", 100, "C", None, None, "SUCCESS"),
        (str(uuid.uuid4()), "2024-12-26 10:02:00", "single", "user1", "192.168.1.1", 2024, "MMXXIV", None, None, "SUCCESS"),
        (str(uuid.uuid4()), "2024-12-26 10:03:00", "range", "user3", "192.168.1.3", None, None, 1, 10, "SUCCESS"),
        (str(uuid.uuid4()), "2024-12-26 10:04:00", "single", "user2", "192.168.1.2", 50, "L", None, None, "SUCCESS"),
        (str(uuid.uuid4()), "2024-12-26 10:05:00", "error", "user4", "192.168.1.4", 0, None, None, None, "ERROR"),
        (str(uuid.uuid4()), "2024-12-26 10:06:00", "single", "user1", "192.168.1.1", 42, "XLII", None, None, "SUCCESS"),  # Duplicate
        (str(uuid.uuid4()), "2024-12-26 11:00:00", "single", "user5", "192.168.1.5", 999, "CMXCIX", None, None, "SUCCESS"),
        (str(uuid.uuid4()), "2024-12-26 11:01:00", "range", "user1", "192.168.1.1", None, None, 100, 200, "SUCCESS"),
        (str(uuid.uuid4()), "2024-12-26 12:00:00", "single", "user2", "192.168.1.2", 7, "VII", None, None, "SUCCESS"),
    ]
    
    columns = ["event_id", "event_timestamp", "conversion_type", "user_id", "client_ip", 
               "input_value", "output_value", "min_range", "max_range", "status"]
    
    bronze_df = spark.createDataFrame(data, columns)
    bronze_df = bronze_df.withColumn("ingested_at", current_timestamp())
    bronze_df = bronze_df.withColumn("is_late_arrival", lit(False))
    bronze_df = bronze_df.withColumn("event_date", date_format("event_timestamp", "yyyy-MM-dd"))
    
    # Create Bronze table
    spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.bronze")
    spark.sql("DROP TABLE IF EXISTS lakehouse.bronze.conversion_events")
    
    bronze_df.writeTo("lakehouse.bronze.conversion_events").create()
    
    print(f"✅ Created Bronze table with {bronze_df.count()} records")
    bronze_df.show(truncate=False)
    
    return bronze_df


def transform_to_silver(spark):
    """Transform Bronze → Silver: Cleanse, deduplicate, enrich."""
    print("\n" + "="*60)
    print("STEP 2: Transforming to Silver Layer")
    print("="*60)
    
    # Read Bronze data
    bronze_df = spark.table("lakehouse.bronze.conversion_events")
    
    # Silver transformations:
    # 1. Filter out errors
    # 2. Deduplicate by input_value (keep latest)
    # 3. Add derived columns
    
    window_spec = Window.partitionBy("input_value").orderBy(col("event_timestamp").desc())
    
    silver_df = (bronze_df
        .filter(col("status") == "SUCCESS")
        .withColumn("row_num", row_number().over(window_spec))
        .filter(col("row_num") == 1)
        .drop("row_num")
        .withColumn("conversion_count", 
            when(col("conversion_type") == "range", col("max_range") - col("min_range") + 1)
            .otherwise(1))
        .withColumn("processed_at", current_timestamp())
    )
    
    # Create Silver table
    spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.silver")
    spark.sql("DROP TABLE IF EXISTS lakehouse.silver.fact_conversions")
    
    silver_df.writeTo("lakehouse.silver.fact_conversions").create()
    
    print(f"✅ Created Silver table with {silver_df.count()} records (deduplicated)")
    silver_df.select("event_id", "user_id", "input_value", "output_value", 
                     "conversion_type", "conversion_count").show(truncate=False)
    
    return silver_df


def aggregate_to_gold(spark):
    """Aggregate Silver → Gold: Analytics-ready metrics."""
    print("\n" + "="*60)
    print("STEP 3: Aggregating to Gold Layer")
    print("="*60)
    
    # Read Silver data
    silver_df = spark.table("lakehouse.silver.fact_conversions")
    
    # Gold aggregations:
    # 1. Daily conversion summary
    # 2. User activity metrics
    # 3. Popular numbers
    
    # --- Gold Table 1: Daily Summary ---
    daily_summary = (silver_df
        .groupBy("event_date")
        .agg(
            count("*").alias("total_conversions"),
            spark_sum("conversion_count").alias("total_numbers_converted"),
            count(when(col("conversion_type") == "single", 1)).alias("single_conversions"),
            count(when(col("conversion_type") == "range", 1)).alias("range_conversions"),
            spark_max("input_value").alias("max_number_converted"),
            spark_min("input_value").alias("min_number_converted")
        )
        .withColumn("generated_at", current_timestamp())
    )
    
    spark.sql("CREATE NAMESPACE IF NOT EXISTS lakehouse.gold")
    spark.sql("DROP TABLE IF EXISTS lakehouse.gold.daily_conversion_summary")
    daily_summary.writeTo("lakehouse.gold.daily_conversion_summary").create()
    
    print("✅ Gold Table: daily_conversion_summary")
    daily_summary.show(truncate=False)
    
    # --- Gold Table 2: User Metrics ---
    user_metrics = (silver_df
        .groupBy("user_id")
        .agg(
            count("*").alias("total_requests"),
            spark_sum("conversion_count").alias("total_numbers"),
            avg("input_value").alias("avg_input_value"),
            spark_max("event_timestamp").alias("last_activity")
        )
        .withColumn("generated_at", current_timestamp())
    )
    
    spark.sql("DROP TABLE IF EXISTS lakehouse.gold.user_metrics")
    user_metrics.writeTo("lakehouse.gold.user_metrics").create()
    
    print("✅ Gold Table: user_metrics")
    user_metrics.show(truncate=False)
    
    # --- Gold Table 3: Popular Numbers ---
    popular_numbers = (silver_df
        .filter(col("input_value").isNotNull())
        .groupBy("input_value", "output_value")
        .agg(count("*").alias("request_count"))
        .orderBy(col("request_count").desc())
        .limit(10)
        .withColumn("generated_at", current_timestamp())
    )
    
    spark.sql("DROP TABLE IF EXISTS lakehouse.gold.popular_numbers")
    popular_numbers.writeTo("lakehouse.gold.popular_numbers").create()
    
    print("✅ Gold Table: popular_numbers")
    popular_numbers.show(truncate=False)
    
    return daily_summary, user_metrics, popular_numbers


def verify_iceberg_tables(spark):
    """Verify all Iceberg tables were created correctly."""
    print("\n" + "="*60)
    print("VERIFICATION: Listing All Tables")
    print("="*60)
    
    for layer in ["bronze", "silver", "gold"]:
        print(f"\n📁 {layer.upper()} Layer:")
        try:
            tables = spark.sql(f"SHOW TABLES IN lakehouse.{layer}").collect()
            for table in tables:
                table_name = f"lakehouse.{layer}.{table['tableName']}"
                count = spark.table(table_name).count()
                print(f"   └── {table['tableName']}: {count} rows")
        except Exception as e:
            print(f"   └── Error: {e}")


def main():
    print("\n" + "🚀"*30)
    print("MEDALLION ARCHITECTURE TEST")
    print("Bronze → Silver → Gold Flow Demonstration")
    print("🚀"*30)
    
    spark = create_spark_session()
    spark.sparkContext.setLogLevel("WARN")
    
    try:
        # Step 1: Create Bronze data
        create_sample_bronze_data(spark)
        
        # Step 2: Transform to Silver
        transform_to_silver(spark)
        
        # Step 3: Aggregate to Gold
        aggregate_to_gold(spark)
        
        # Verify all tables
        verify_iceberg_tables(spark)
        
        print("\n" + "✅"*30)
        print("TEST COMPLETED SUCCESSFULLY!")
        print("✅"*30)
        print("\nYou can now query these tables in:")
        print("  - Spark SQL: spark.table('lakehouse.gold.daily_conversion_summary')")
        print("  - Jupyter: Connect to Spark and explore")
        print("  - Superset: Add Iceberg as data source")
        
    except Exception as e:
        print(f"\n❌ Error: {e}")
        import traceback
        traceback.print_exc()
    finally:
        spark.stop()


if __name__ == "__main__":
    main()

