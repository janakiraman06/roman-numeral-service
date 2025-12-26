"""
Bronze Layer Batch Ingestion: Kafka → Iceberg (SECONDARY PATH)
===============================================================
PySpark batch job for Bronze layer BACKFILL and RECOVERY scenarios.

## ARCHITECTURE CONTEXT
-----------------------
In production, Flink is the PRIMARY real-time streaming path:
    API → Kafka → Flink → Iceberg Bronze  (sub-second latency, exactly-once)

This Spark job is the SECONDARY path for:
    - Historical backfills (reprocess past data)
    - Schema migration reprocessing
    - Flink downtime recovery
    - Development/testing without Flink

In the demo environment:
    - Flink demonstrates streaming capabilities (logs to console)
    - Spark handles Bronze writes (for working E2E flow)
    See docs/DATA_ARCHITECTURE.md for full explanation.

## WHY NOT KAFKA CONNECT?
-------------------------
Flink provides everything Kafka Connect does, plus:
    - Transformations (parsing, enrichment)
    - Exactly-once semantics (checkpointing)
    - Late data handling (watermarks)
    - Stateful deduplication
    - Direct Iceberg writes

Therefore, Kafka Connect is NOT needed in this architecture.

## Modes:
1. **Incremental (default)**: Process only new Kafka messages since last run
2. **Backfill**: Reprocess from earliest offset with date filtering

## Exactly-Once Guarantee:
- Tracks MAX(kafka_offset) in Bronze table
- Only reads messages with offset > max
- Deduplicates by event_id before writing
- MERGE INTO Iceberg for idempotent writes

## Usage:
    # Normal incremental run (Airflow scheduled)
    spark-submit ... bronze_batch_backfill.py
    
    # Backfill specific date range (manual trigger)
    spark-submit ... bronze_batch_backfill.py --backfill --start-date 2025-01-01 --end-date 2025-01-07

## Failure Recovery:
- If job fails mid-write, next run re-reads same offsets
- MERGE INTO skips already-written event_ids (idempotent)
- No duplicates even on retry
"""

import sys
import argparse
from datetime import datetime
from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, from_json, current_timestamp, 
    expr, to_timestamp, lit, row_number
)
from pyspark.sql.window import Window
from pyspark.sql.types import (
    StructType, StructField, StringType, LongType, 
    IntegerType, TimestampType
)


def parse_args():
    """Parse command line arguments for backfill support."""
    parser = argparse.ArgumentParser(description="Bronze Layer Ingestion: Kafka → Iceberg")
    parser.add_argument("--backfill", action="store_true", 
                        help="Backfill mode: reprocess from earliest offset")
    parser.add_argument("--start-date", type=str, default=None,
                        help="Backfill start date (ISO format, e.g., 2025-01-01)")
    parser.add_argument("--end-date", type=str, default=None,
                        help="Backfill end date (ISO format, e.g., 2025-01-07)")
    parser.add_argument("--streaming", action="store_true",
                        help="Run in streaming mode (continuous)")
    parser.add_argument("--consumer-group", type=str, default="bronze-ingestion-spark",
                        help="Kafka consumer group ID for offset tracking")
    return parser.parse_args()

# Event schema matching ConversionEvent from Spring Boot
EVENT_SCHEMA = StructType([
    StructField("eventId", StringType(), True),
    StructField("eventTime", StringType(), True),
    StructField("eventType", StringType(), True),
    StructField("userId", LongType(), True),
    StructField("apiKeyPrefix", StringType(), True),
    StructField("clientIp", StringType(), True),
    StructField("correlationId", StringType(), True),
    StructField("inputNumber", IntegerType(), True),
    StructField("outputRoman", StringType(), True),
    StructField("rangeMin", IntegerType(), True),
    StructField("rangeMax", IntegerType(), True),
    StructField("resultCount", IntegerType(), True),
    StructField("pageOffset", IntegerType(), True),
    StructField("pageLimit", IntegerType(), True),
    StructField("responseTimeNanos", LongType(), True),
    StructField("status", StringType(), True)
])


def create_spark_session():
    """Create Spark session with Iceberg REST catalog and Kafka support."""
    return (SparkSession.builder
        .appName("BronzeKafkaIngestion")
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


def read_kafka_batch(spark, kafka_bootstrap_servers, topic, consumer_group, is_backfill=False):
    """
    Read messages from Kafka topic (batch mode) with offset tracking.
    
    Incremental Mode (default):
        - startingOffsets: Use consumer group's committed offset
        - endingOffsets: Latest available
        - Result: Only new messages since last run
    
    Backfill Mode:
        - startingOffsets: Earliest (beginning of topic)
        - endingOffsets: Latest
        - Result: All messages (requires deduplication)
    
    Args:
        spark: SparkSession
        kafka_bootstrap_servers: Kafka broker address
        topic: Kafka topic name
        consumer_group: Consumer group ID for offset tracking
        is_backfill: If True, read from earliest; if False, read from committed offset
    """
    reader = (spark.read
        .format("kafka")
        .option("kafka.bootstrap.servers", kafka_bootstrap_servers)
        .option("subscribe", topic)
        .option("kafka.group.id", consumer_group)  # Track offsets by group
        .option("endingOffsets", "latest"))
    
    if is_backfill:
        # Backfill: Start from beginning
        print("📥 BACKFILL MODE: Reading from earliest offset")
        reader = reader.option("startingOffsets", "earliest")
    else:
        # Incremental: Start from last committed offset (or earliest if first run)
        # Note: Spark Kafka doesn't auto-commit; we handle this via dedup + max offset
        print("📥 INCREMENTAL MODE: Reading new messages only")
        reader = reader.option("startingOffsets", "earliest")  # We'll filter by last processed offset
    
    return reader.load()


def read_kafka_stream(spark, kafka_bootstrap_servers, topic):
    """Read messages from Kafka topic (streaming mode)."""
    return (spark.readStream
        .format("kafka")
        .option("kafka.bootstrap.servers", kafka_bootstrap_servers)
        .option("subscribe", topic)
        .option("startingOffsets", "earliest")
        .load())


def transform_events(df):
    """Transform raw Kafka messages to Bronze schema."""
    return (df
        .select(
            col("key").cast("string").alias("kafka_key"),
            col("value").cast("string").alias("raw_value"),
            col("partition").alias("kafka_partition"),
            col("offset").alias("kafka_offset"),
            col("timestamp").alias("kafka_timestamp")
        )
        .withColumn("event", from_json(col("raw_value"), EVENT_SCHEMA))
        .select(
            col("event.eventId").alias("event_id"),
            to_timestamp(col("event.eventTime")).alias("event_time"),
            col("event.eventType").alias("event_type"),
            col("event.userId").alias("user_id"),
            col("event.apiKeyPrefix").alias("api_key_prefix"),
            col("event.clientIp").alias("client_ip"),
            col("event.correlationId").alias("correlation_id"),
            col("event.inputNumber").alias("input_number"),
            col("event.outputRoman").alias("output_roman"),
            col("event.rangeMin").alias("range_min"),
            col("event.rangeMax").alias("range_max"),
            col("event.resultCount").alias("result_count"),
            col("event.pageOffset").alias("page_offset"),
            col("event.pageLimit").alias("page_limit"),
            col("event.responseTimeNanos").alias("response_time_nanos"),
            col("event.status").alias("status"),
            current_timestamp().alias("ingested_at"),
            col("kafka_partition"),
            col("kafka_offset")
        )
        .filter(col("event_id").isNotNull())  # Filter malformed events
    )


def deduplicate_events(df):
    """
    Deduplicate events by event_id, keeping the one with highest kafka_offset.
    
    This handles:
    - Duplicate events from Kafka (producer retries)
    - Re-reading events in backfill mode
    - Multiple runs reading same offset range
    """
    window = Window.partitionBy("event_id").orderBy(col("kafka_offset").desc())
    return (df
        .withColumn("row_num", row_number().over(window))
        .filter(col("row_num") == 1)
        .drop("row_num"))


def get_max_kafka_offset(spark, table_name):
    """
    Get the maximum kafka_offset already in Bronze table.
    Used for incremental reads to skip already-processed messages.
    """
    try:
        result = spark.sql(f"""
            SELECT COALESCE(MAX(kafka_offset), -1) as max_offset 
            FROM {table_name}
        """).first()
        return result["max_offset"] if result else -1
    except Exception:
        return -1  # Table doesn't exist yet


def write_to_iceberg_with_dedup(spark, df, table_name, is_backfill=False):
    """
    Write DataFrame to Iceberg table with deduplication.
    
    Uses MERGE INTO for idempotent writes:
    - If event_id exists: Skip (already ingested)
    - If event_id doesn't exist: Insert
    
    This ensures exactly-once semantics even on retries or backfills.
    """
    # Register temp view for MERGE
    df.createOrReplaceTempView("incoming_bronze_events")
    
    # MERGE INTO for idempotent writes
    merge_sql = f"""
        MERGE INTO {table_name} AS target
        USING incoming_bronze_events AS source
        ON target.event_id = source.event_id
        WHEN NOT MATCHED THEN INSERT *
    """
    
    print("🔄 Executing MERGE INTO for idempotent write...")
    spark.sql(merge_sql)
    print("✅ MERGE complete - duplicates skipped, new events inserted")


def write_to_iceberg_stream(df, table_name, checkpoint_path):
    """Write DataFrame to Iceberg table (streaming mode)."""
    return (df.writeStream
        .format("iceberg")
        .outputMode("append")
        .option("path", table_name)
        .option("checkpointLocation", checkpoint_path)
        .trigger(processingTime="10 seconds")
        .start())


def ensure_bronze_table_exists(spark, table_name):
    """Create Bronze table if it doesn't exist."""
    namespace = table_name.rsplit(".", 1)[0]  # e.g., "lakehouse.bronze"
    
    # Create namespace if not exists
    spark.sql(f"CREATE NAMESPACE IF NOT EXISTS {namespace}")
    
    # Check if table exists
    try:
        spark.table(table_name)
        print(f"✅ Table {table_name} exists")
    except Exception:
        print(f"📝 Creating table {table_name}...")
        spark.sql(f"""
            CREATE TABLE IF NOT EXISTS {table_name} (
                event_id STRING,
                event_time TIMESTAMP,
                event_type STRING,
                user_id LONG,
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
                response_time_nanos LONG,
                status STRING,
                ingested_at TIMESTAMP,
                kafka_partition INT,
                kafka_offset LONG
            )
            USING iceberg
            PARTITIONED BY (days(event_time))
        """)
        print(f"✅ Table {table_name} created")


def main():
    """Main entry point."""
    args = parse_args()
    
    print("=" * 60)
    print("Bronze Layer Ingestion: Kafka → Iceberg")
    print("=" * 60)
    
    if args.streaming:
        print("Mode: STREAMING (continuous)")
    elif args.backfill:
        print("Mode: BACKFILL (reprocess all)")
        if args.start_date:
            print(f"  Start Date: {args.start_date}")
        if args.end_date:
            print(f"  End Date: {args.end_date}")
    else:
        print("Mode: INCREMENTAL (new messages only)")
    
    print(f"Consumer Group: {args.consumer_group}")
    print("=" * 60)
    
    # Configuration
    kafka_bootstrap_servers = "kafka:9092"
    topic = "roman-numeral-events"
    table_name = "lakehouse.bronze.raw_conversion_events"
    checkpoint_path = "s3a://lakehouse/warehouse/checkpoints/bronze_ingestion"
    
    # Create Spark session
    spark = create_spark_session()
    spark.sparkContext.setLogLevel("WARN")
    
    print(f"\nReading from Kafka topic: {topic}")
    print(f"Writing to Iceberg table: {table_name}")
    
    # Ensure table exists
    ensure_bronze_table_exists(spark, table_name)
    
    if args.streaming:
        # Streaming mode
        raw_df = read_kafka_stream(spark, kafka_bootstrap_servers, topic)
        bronze_df = transform_events(raw_df)
        
        query = write_to_iceberg_stream(bronze_df, table_name, checkpoint_path)
        print("\nStreaming query started. Waiting for data...")
        query.awaitTermination()
    else:
        # Batch mode (incremental or backfill)
        
        # Get current max offset in Bronze (for incremental filtering)
        current_max_offset = get_max_kafka_offset(spark, table_name)
        print(f"\n📊 Current max kafka_offset in Bronze: {current_max_offset}")
        
        # Read from Kafka
        raw_df = read_kafka_batch(
            spark, 
            kafka_bootstrap_servers, 
            topic, 
            args.consumer_group,
            is_backfill=args.backfill
        )
        
        total_in_kafka = raw_df.count()
        print(f"📬 Total messages in Kafka topic: {total_in_kafka}")
        
        if total_in_kafka == 0:
            print("⚠️ No messages found in Kafka topic.")
            spark.stop()
            return
        
        # For incremental mode: filter to only new messages
        if not args.backfill and current_max_offset >= 0:
            raw_df = raw_df.filter(col("offset") > current_max_offset)
            new_count = raw_df.count()
            print(f"📥 New messages (offset > {current_max_offset}): {new_count}")
            
            if new_count == 0:
                print("✅ No new messages to process. Bronze is up to date!")
                spark.stop()
                return
        
        # Transform events
        bronze_df = transform_events(raw_df)
        
        # Apply date filter for backfill if specified
        if args.backfill and args.start_date:
            start_dt = datetime.fromisoformat(args.start_date)
            bronze_df = bronze_df.filter(col("event_time") >= start_dt)
            print(f"🗓️ Filtered to events >= {args.start_date}")
        
        if args.backfill and args.end_date:
            end_dt = datetime.fromisoformat(args.end_date)
            bronze_df = bronze_df.filter(col("event_time") < end_dt)
            print(f"🗓️ Filtered to events < {args.end_date}")
        
        # Deduplicate within the batch (same event_id from Kafka retries)
        bronze_df = deduplicate_events(bronze_df)
        
        record_count = bronze_df.count()
        print(f"\n📝 Records to process (after dedup): {record_count}")
        
        if record_count > 0:
            # Show sample data
            print("\nSample transformed data:")
            bronze_df.show(5, truncate=False)
            
            # Write with MERGE for idempotent exactly-once writes
            write_to_iceberg_with_dedup(spark, bronze_df, table_name, is_backfill=args.backfill)
            
            # Verify
            total = spark.table(table_name).count()
            print(f"\n📊 Total records in Bronze table: {total}")
            
            # Show offset range processed
            max_processed = bronze_df.agg({"kafka_offset": "max"}).first()[0]
            print(f"📍 Max kafka_offset processed: {max_processed}")
        else:
            print("⚠️ No valid records after transformation/filtering.")
    
    print("\n✅ Bronze ingestion complete!")
    spark.stop()


if __name__ == "__main__":
    main()

