"""
Bronze Layer Batch Backfill: Kafka → Iceberg
=============================================
PySpark batch job for Bronze layer backfill and recovery scenarios.

NOTE: For real-time streaming ingestion, use the Flink job instead:
      data-platform/flink/bronze-ingestion/

This Spark job is intended for:
- Historical data backfills
- Reprocessing after schema changes
- Recovery when Flink is unavailable
- Development/testing without Flink complexity

Usage:
    spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.12:3.5.0,org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.5.0 \
        bronze_batch_backfill.py

Or run in streaming mode (for testing):
    spark-submit ... bronze_batch_backfill.py --streaming

Architecture:
    Real-time (Flink):  API → Kafka → Flink → Bronze (primary path)
    Batch (Spark):      API → Kafka → Spark → Bronze (backfill/recovery)
"""

import sys
from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, from_json, current_timestamp, 
    expr, to_timestamp, lit
)
from pyspark.sql.types import (
    StructType, StructField, StringType, LongType, 
    IntegerType, TimestampType
)

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
    """Create Spark session with Iceberg and Kafka support."""
    return (SparkSession.builder
        .appName("BronzeKafkaIngestion")
        .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
        .config("spark.sql.catalog.lakehouse", "org.apache.iceberg.spark.SparkCatalog")
        .config("spark.sql.catalog.lakehouse.type", "hadoop")
        .config("spark.sql.catalog.lakehouse.warehouse", "s3a://lakehouse/")
        .config("spark.hadoop.fs.s3a.endpoint", "http://minio:9000")
        .config("spark.hadoop.fs.s3a.access.key", "minioadmin")
        .config("spark.hadoop.fs.s3a.secret.key", "minioadmin123")
        .config("spark.hadoop.fs.s3a.path.style.access", "true")
        .config("spark.hadoop.fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
        .getOrCreate())


def read_kafka_batch(spark, kafka_bootstrap_servers, topic):
    """Read all messages from Kafka topic (batch mode)."""
    return (spark.read
        .format("kafka")
        .option("kafka.bootstrap.servers", kafka_bootstrap_servers)
        .option("subscribe", topic)
        .option("startingOffsets", "earliest")
        .option("endingOffsets", "latest")
        .load())


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


def write_to_iceberg_batch(df, table_name):
    """Write DataFrame to Iceberg table (batch mode)."""
    (df.writeTo(table_name)
        .option("merge-schema", "true")
        .append())


def write_to_iceberg_stream(df, table_name, checkpoint_path):
    """Write DataFrame to Iceberg table (streaming mode)."""
    return (df.writeStream
        .format("iceberg")
        .outputMode("append")
        .option("path", table_name)
        .option("checkpointLocation", checkpoint_path)
        .trigger(processingTime="10 seconds")
        .start())


def main():
    """Main entry point."""
    streaming_mode = "--streaming" in sys.argv
    
    print("=" * 60)
    print("Bronze Layer Ingestion: Kafka → Iceberg")
    print(f"Mode: {'Streaming' if streaming_mode else 'Batch'}")
    print("=" * 60)
    
    # Configuration
    kafka_bootstrap_servers = "kafka:9092"
    topic = "roman-numeral-events"
    table_name = "lakehouse.bronze.conversion_events"
    checkpoint_path = "s3a://lakehouse/checkpoints/bronze_ingestion"
    
    # Create Spark session
    spark = create_spark_session()
    spark.sparkContext.setLogLevel("WARN")
    
    print(f"\nReading from Kafka topic: {topic}")
    print(f"Writing to Iceberg table: {table_name}")
    
    if streaming_mode:
        # Streaming mode
        raw_df = read_kafka_stream(spark, kafka_bootstrap_servers, topic)
        bronze_df = transform_events(raw_df)
        
        query = write_to_iceberg_stream(bronze_df, table_name, checkpoint_path)
        print("\nStreaming query started. Waiting for data...")
        query.awaitTermination()
    else:
        # Batch mode
        raw_df = read_kafka_batch(spark, kafka_bootstrap_servers, topic)
        
        message_count = raw_df.count()
        print(f"\nFound {message_count} messages in Kafka")
        
        if message_count > 0:
            bronze_df = transform_events(raw_df)
            
            # Show sample data
            print("\nSample transformed data:")
            bronze_df.show(5, truncate=False)
            
            # Write to Iceberg
            print(f"\nWriting {bronze_df.count()} records to Bronze table...")
            write_to_iceberg_batch(bronze_df, table_name)
            print("✅ Bronze ingestion complete!")
        else:
            print("No messages found in Kafka topic.")
    
    spark.stop()


if __name__ == "__main__":
    main()

