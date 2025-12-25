package com.adobe.romannumeral.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event representing a Roman numeral conversion for streaming to Kafka.
 *
 * <p>This event is published to the Bronze layer topic for downstream
 * processing by Spark and storage in the data lake (Iceberg tables).</p>
 *
 * <p>Design follows event sourcing principles:
 * <ul>
 *   <li>Immutable event record</li>
 *   <li>Contains all context needed for replay</li>
 *   <li>Unique event ID for deduplication</li>
 *   <li>Timestamp for event time processing</li>
 * </ul>
 * </p>
 *
 * @see com.adobe.romannumeral.service.KafkaProducerService
 */
public record ConversionEvent(
        /** Unique event identifier for deduplication */
        String eventId,
        
        /** Event timestamp (ISO-8601) */
        Instant eventTime,
        
        /** Event type: SINGLE or RANGE */
        String eventType,
        
        /** User ID if authenticated, null otherwise */
        Long userId,
        
        /** API key prefix if authenticated */
        String apiKeyPrefix,
        
        /** Client IP address */
        String clientIp,
        
        /** Correlation ID for request tracing */
        String correlationId,
        
        // Single conversion fields
        /** Input number for single conversion */
        Integer inputNumber,
        
        /** Output Roman numeral for single conversion */
        String outputRoman,
        
        // Range conversion fields
        /** Minimum value for range conversion */
        Integer rangeMin,
        
        /** Maximum value for range conversion */
        Integer rangeMax,
        
        /** Number of results returned */
        Integer resultCount,
        
        /** Pagination offset */
        Integer pageOffset,
        
        /** Pagination limit */
        Integer pageLimit,
        
        // Performance metrics
        /** Response time in nanoseconds */
        Long responseTimeNanos,
        
        /** Processing status: SUCCESS, ERROR */
        String status
) {
    /**
     * Creates a single conversion event.
     */
    public static ConversionEvent single(
            Integer input, 
            String output, 
            long responseTimeNanos,
            String correlationId,
            String clientIp,
            Long userId,
            String apiKeyPrefix) {
        return new ConversionEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                "SINGLE",
                userId,
                apiKeyPrefix,
                clientIp,
                correlationId,
                input,
                output,
                null, null, null, null, null,
                responseTimeNanos,
                "SUCCESS"
        );
    }

    /**
     * Creates a range conversion event.
     */
    public static ConversionEvent range(
            Integer min,
            Integer max,
            int resultCount,
            Integer offset,
            Integer limit,
            long responseTimeNanos,
            String correlationId,
            String clientIp,
            Long userId,
            String apiKeyPrefix) {
        return new ConversionEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                "RANGE",
                userId,
                apiKeyPrefix,
                clientIp,
                correlationId,
                null, null,
                min, max, resultCount, offset, limit,
                responseTimeNanos,
                "SUCCESS"
        );
    }

    /**
     * Creates an error event.
     */
    public static ConversionEvent error(
            String eventType,
            String errorStatus,
            String correlationId,
            String clientIp) {
        return new ConversionEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                eventType,
                null, null,
                clientIp,
                correlationId,
                null, null, null, null, null, null, null,
                0L,
                errorStatus
        );
    }
}

