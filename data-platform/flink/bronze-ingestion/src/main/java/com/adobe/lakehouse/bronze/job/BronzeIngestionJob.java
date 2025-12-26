package com.adobe.lakehouse.bronze.job;

import com.adobe.lakehouse.bronze.function.DeduplicationFunction;
import com.adobe.lakehouse.bronze.model.BronzeRecord;
import com.adobe.lakehouse.bronze.model.ConversionEvent;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Bronze Layer Streaming Ingestion Job.
 * 
 * <p>Demonstrates production-grade streaming patterns for Bronze layer ingestion:</p>
 * <ul>
 *   <li><b>Exactly-Once Semantics</b>: Flink checkpointing with Kafka offsets</li>
 *   <li><b>Late Arriving Data</b>: 5-minute watermark delay, side output for late events</li>
 *   <li><b>Deduplication</b>: Keyed by event_id with 24-hour state TTL</li>
 *   <li><b>Error Handling</b>: Null filtering for malformed events</li>
 *   <li><b>Failure Recovery</b>: Externalized checkpoints for state recovery</li>
 * </ul>
 * 
 * <h3>Architecture Note:</h3>
 * <p>This job demonstrates real-time streaming capabilities. In production, the output
 * would be written to Iceberg Bronze layer. For local demo, output is logged to console
 * while Spark batch handles the actual Bronze table writes.</p>
 * 
 * <h3>Job Topology:</h3>
 * <pre>
 * Kafka Source (roman-numeral-events)
 *       │
 *       ▼
 * JSON Parsing (ConversionEvent)
 *       │
 *       ▼
 * Null/Invalid Filtering
 *       │
 *       ▼
 * Watermark Assignment (5-min delay)
 *       │
 *       ▼
 * Key By event_id
 *       │
 *       ▼
 * Deduplication (24h state TTL)
 *       │
 *       ├─────────────────┐
 *       ▼                 ▼
 * Main Output        Late Arrivals
 * (Console/Iceberg)  (Side Output)
 * </pre>
 * 
 * @author Roman Numeral Service Data Platform
 * @version 1.0.0
 */
public class BronzeIngestionJob {
    
    private static final Logger LOG = LoggerFactory.getLogger(BronzeIngestionJob.class);
    
    // Configuration constants
    private static final String JOB_NAME = "Bronze Layer Ingestion";
    
    // Kafka configuration
    private static final String KAFKA_BROKERS = getEnvOrDefault(
            "KAFKA_BOOTSTRAP_SERVERS", "kafka:9092");
    private static final String INPUT_TOPIC = getEnvOrDefault(
            "KAFKA_INPUT_TOPIC", "roman-numeral-events");
    private static final String CONSUMER_GROUP = getEnvOrDefault(
            "KAFKA_CONSUMER_GROUP", "flink-bronze-ingestion");
    
    // Watermark configuration
    private static final Duration WATERMARK_DELAY = Duration.ofMinutes(5);
    private static final Duration WATERMARK_IDLENESS = Duration.ofMinutes(1);
    
    // Checkpoint configuration
    private static final long CHECKPOINT_INTERVAL_MS = 60_000;  // 60 seconds
    private static final long CHECKPOINT_TIMEOUT_MS = 600_000;  // 10 minutes
    private static final int CHECKPOINT_MIN_PAUSE_MS = 30_000;  // 30 seconds
    
    public static void main(String[] args) throws Exception {
        LOG.info("============================================================");
        LOG.info("Bronze Layer Streaming Ingestion (Apache Flink)");
        LOG.info("============================================================");
        LOG.info("Kafka Brokers: {}", KAFKA_BROKERS);
        LOG.info("Input Topic: {}", INPUT_TOPIC);
        LOG.info("Consumer Group: {}", CONSUMER_GROUP);
        LOG.info("Watermark Delay: {}", WATERMARK_DELAY);
        LOG.info("Checkpoint Interval: {}ms", CHECKPOINT_INTERVAL_MS);
        LOG.info("============================================================");
        
        // Create execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Configure checkpointing for exactly-once
        configureCheckpointing(env);
        
        // Build the job pipeline
        buildPipeline(env);
        
        // Execute the job
        LOG.info("Starting Flink job: {}", JOB_NAME);
        env.execute(JOB_NAME);
    }
    
    /**
     * Configures checkpointing for exactly-once semantics.
     */
    private static void configureCheckpointing(StreamExecutionEnvironment env) {
        // Enable checkpointing
        env.enableCheckpointing(CHECKPOINT_INTERVAL_MS, CheckpointingMode.EXACTLY_ONCE);
        
        CheckpointConfig checkpointConfig = env.getCheckpointConfig();
        
        // Minimum time between checkpoints
        checkpointConfig.setMinPauseBetweenCheckpoints(CHECKPOINT_MIN_PAUSE_MS);
        
        // Checkpoint timeout
        checkpointConfig.setCheckpointTimeout(CHECKPOINT_TIMEOUT_MS);
        
        // Only one checkpoint at a time
        checkpointConfig.setMaxConcurrentCheckpoints(1);
        
        // Retain checkpoints on cancellation (for recovery)
        checkpointConfig.setExternalizedCheckpointCleanup(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        
        LOG.info("Checkpointing configured: interval={}ms, timeout={}ms, mode=EXACTLY_ONCE",
                CHECKPOINT_INTERVAL_MS, CHECKPOINT_TIMEOUT_MS);
    }
    
    /**
     * Builds the Flink job pipeline.
     */
    private static void buildPipeline(StreamExecutionEnvironment env) {
        // 1. Create Kafka source
        KafkaSource<String> kafkaSource = createKafkaSource();
        
        // 2. Create watermark strategy with bounded out-of-orderness
        WatermarkStrategy<ConversionEvent> watermarkStrategy = WatermarkStrategy
                .<ConversionEvent>forBoundedOutOfOrderness(WATERMARK_DELAY)
                .withIdleness(WATERMARK_IDLENESS)
                .withTimestampAssigner((event, timestamp) -> event.getTimestampMillis());
        
        // 3. Read from Kafka and parse JSON
        DataStream<String> rawStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.noWatermarks(),
                "Kafka Source"
        );
        
        // 4. Parse JSON to ConversionEvent (with error handling)
        SingleOutputStreamOperator<ConversionEvent> parsedStream = rawStream
                .map(json -> {
                    try {
                        return ConversionEvent.fromJson(json);
                    } catch (Exception e) {
                        LOG.warn("Failed to parse JSON: {}. Error: {}", 
                                json.substring(0, Math.min(100, json.length())), e.getMessage());
                        return null;
                    }
                })
                .name("JSON Parser")
                .filter(event -> event != null && event.getEventId() != null)
                .name("Filter Invalid Events");
        
        // 5. Assign watermarks
        DataStream<ConversionEvent> watermarkedStream = parsedStream
                .assignTimestampsAndWatermarks(watermarkStrategy)
                .name("Watermark Assignment");
        
        // 6. Key by event_id and deduplicate
        SingleOutputStreamOperator<BronzeRecord> deduplicatedStream = watermarkedStream
                .keyBy(ConversionEvent::getEventId)
                .process(new DeduplicationFunction())
                .name("Deduplication");
        
        // 7. Get late arrivals side output
        DataStream<BronzeRecord> lateArrivals = deduplicatedStream
                .getSideOutput(DeduplicationFunction.LATE_ARRIVALS_TAG);
        
        // 8. Log late arrivals
        lateArrivals
                .map(record -> {
                    LOG.warn("Late arrival detected: eventId={}, timestamp={}", 
                            record.getEventId(), record.getEventTimestamp());
                    return record;
                })
                .name("Late Arrival Logger");
        
        // 9. Main output - print to console for demo
        // In production, this would write to Iceberg Bronze layer
        deduplicatedStream
                .map(record -> String.format("BRONZE_EVENT: id=%s, type=%s, input=%s, output=%s",
                        record.getEventId(),
                        record.getConversionType(),
                        record.getInputValue(),
                        record.getOutputValue()))
                .print()
                .name("Bronze Output (Demo)");
        
        LOG.info("Pipeline built successfully");
        LOG.info("NOTE: In production, output would be written to Iceberg Bronze layer.");
        LOG.info("      For demo, events are printed to console. Check TaskManager logs.");
    }
    
    /**
     * Creates the Kafka source with exactly-once configuration.
     */
    private static KafkaSource<String> createKafkaSource() {
        return KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BROKERS)
                .setTopics(INPUT_TOPIC)
                .setGroupId(CONSUMER_GROUP)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperty("enable.auto.commit", "false")
                .setProperty("isolation.level", "read_committed")
                .build();
    }
    
    /**
     * Gets an environment variable with a default fallback.
     */
    private static String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
}
