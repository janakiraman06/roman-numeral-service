package com.adobe.lakehouse.bronze.function;

import com.adobe.lakehouse.bronze.model.BronzeRecord;
import com.adobe.lakehouse.bronze.model.ConversionEvent;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deduplication function using Flink keyed state.
 * 
 * <h3>Data Engineering Standards Implemented:</h3>
 * <ul>
 *   <li><b>Deduplication</b>: Uses event_id as key, first-event-wins semantics</li>
 *   <li><b>State TTL</b>: 24-hour expiration to prevent unbounded state growth</li>
 *   <li><b>Late Arrival Detection</b>: Compares event time to current watermark</li>
 *   <li><b>Metrics</b>: Tracks duplicates and late arrivals</li>
 * </ul>
 * 
 * <h3>State Management:</h3>
 * <pre>
 * Key: event_id
 * Value: Boolean (true = seen)
 * TTL: 24 hours (configured in StateTtlConfig)
 * </pre>
 * 
 * @author Roman Numeral Service Data Platform
 * @version 1.0.0
 */
public class DeduplicationFunction 
        extends KeyedProcessFunction<String, ConversionEvent, BronzeRecord> {
    
    private static final Logger LOG = LoggerFactory.getLogger(DeduplicationFunction.class);
    private static final long serialVersionUID = 1L;
    
    // State TTL: 24 hours
    private static final long STATE_TTL_HOURS = 24;
    
    // Output tag for late arrivals (can be used for side output)
    public static final OutputTag<BronzeRecord> LATE_ARRIVALS_TAG = 
            new OutputTag<>("late-arrivals") {};
    
    // Keyed state to track seen event IDs
    private transient ValueState<Boolean> seenState;
    
    // Kafka metadata (passed via context)
    private int kafkaPartition = 0;
    private long kafkaOffset = 0L;
    
    // Metrics
    private transient long duplicateCount = 0;
    private transient long lateArrivalCount = 0;
    private transient long processedCount = 0;
    
    @Override
    public void open(Configuration parameters) throws Exception {
        // Configure state with TTL
        StateTtlConfig ttlConfig = StateTtlConfig.newBuilder(Time.hours(STATE_TTL_HOURS))
                .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                .cleanupFullSnapshot()
                .build();
        
        // Create state descriptor with TTL
        ValueStateDescriptor<Boolean> stateDescriptor = 
                new ValueStateDescriptor<>("seen-events", Boolean.class);
        stateDescriptor.enableTimeToLive(ttlConfig);
        
        // Initialize state
        seenState = getRuntimeContext().getState(stateDescriptor);
        
        LOG.info("DeduplicationFunction initialized with {}h state TTL", STATE_TTL_HOURS);
    }
    
    @Override
    public void processElement(
            ConversionEvent event,
            Context ctx,
            Collector<BronzeRecord> out) throws Exception {
        
        // Check if we've seen this event
        Boolean seen = seenState.value();
        
        if (Boolean.TRUE.equals(seen)) {
            // Duplicate event - skip
            duplicateCount++;
            if (duplicateCount % 1000 == 0) {
                LOG.debug("Duplicate event skipped: {}. Total duplicates: {}", 
                        event.getEventId(), duplicateCount);
            }
            return;
        }
        
        // Mark as seen
        seenState.update(true);
        processedCount++;
        
        // Check if this is a late arrival
        long currentWatermark = ctx.timerService().currentWatermark();
        long eventTime = event.getTimestampMillis();
        boolean isLateArrival = eventTime < currentWatermark;
        
        if (isLateArrival) {
            lateArrivalCount++;
            LOG.info("Late arrival detected: eventId={}, eventTime={}, watermark={}",
                    event.getEventId(), eventTime, currentWatermark);
        }
        
        // Create Bronze record
        BronzeRecord record = BronzeRecord.fromEvent(
                event, 
                kafkaPartition, 
                kafkaOffset, 
                isLateArrival
        );
        
        // Emit to main output
        out.collect(record);
        
        // Also emit to side output if late
        if (isLateArrival) {
            ctx.output(LATE_ARRIVALS_TAG, record);
        }
        
        // Log progress periodically
        if (processedCount % 10000 == 0) {
            LOG.info("Processed {} events. Duplicates: {}, Late arrivals: {}",
                    processedCount, duplicateCount, lateArrivalCount);
        }
    }
    
    /**
     * Sets Kafka metadata for the current record.
     * Called before processElement via a wrapping function.
     */
    public void setKafkaMetadata(int partition, long offset) {
        this.kafkaPartition = partition;
        this.kafkaOffset = offset;
    }
}

