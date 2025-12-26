package com.adobe.lakehouse.bronze.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Represents a record in the Bronze layer Iceberg table.
 * 
 * <p>This class adds audit columns and partition fields to the raw event data.
 * It is the target schema for the Bronze layer ingestion.</p>
 * 
 * <h3>Additional Fields (vs ConversionEvent):</h3>
 * <ul>
 *   <li>{@code ingestedAt} - When the event was ingested to Bronze</li>
 *   <li>{@code isLateArrival} - Whether the event arrived after the watermark</li>
 *   <li>{@code kafkaPartition} - Source Kafka partition</li>
 *   <li>{@code kafkaOffset} - Source Kafka offset</li>
 *   <li>{@code year}, {@code month}, {@code day} - Partition columns</li>
 * </ul>
 * 
 * @author Roman Numeral Service Data Platform
 * @version 1.0.0
 */
public class BronzeRecord implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Original event fields
    private String eventId;
    private Instant eventTimestamp;
    private String userId;
    private String apiKeyId;
    private String clientIp;
    private String userAgent;
    private String conversionType;
    private Integer inputValue;
    private String outputValue;
    private Integer minRange;
    private Integer maxRange;
    private String rangeResults;
    private String errorType;
    private String errorMessage;
    
    // Audit fields
    private Instant ingestedAt;
    private boolean isLateArrival;
    private int kafkaPartition;
    private long kafkaOffset;
    
    // Partition fields (derived from eventTimestamp)
    private int year;
    private int month;
    private int day;
    
    /**
     * Creates a BronzeRecord from a ConversionEvent.
     * 
     * @param event The source conversion event
     * @param kafkaPartition The Kafka partition the event came from
     * @param kafkaOffset The Kafka offset of the event
     * @param isLateArrival Whether this event arrived after the watermark
     * @return A new BronzeRecord
     */
    public static BronzeRecord fromEvent(
            ConversionEvent event,
            int kafkaPartition,
            long kafkaOffset,
            boolean isLateArrival) {
        
        BronzeRecord record = new BronzeRecord();
        
        // Copy event fields
        record.eventId = event.getEventId();
        record.eventTimestamp = event.getTimestamp();
        record.userId = event.getUserId();
        record.apiKeyId = event.getApiKeyId();
        record.clientIp = event.getClientIp();
        record.userAgent = event.getUserAgent();
        record.conversionType = event.getConversionType();
        record.inputValue = event.getInputValue();
        record.outputValue = event.getOutputValue();
        record.minRange = event.getMinRange();
        record.maxRange = event.getMaxRange();
        record.rangeResults = event.getRangeResults();
        record.errorType = event.getErrorType();
        record.errorMessage = event.getErrorMessage();
        
        // Set audit fields
        record.ingestedAt = Instant.now();
        record.isLateArrival = isLateArrival;
        record.kafkaPartition = kafkaPartition;
        record.kafkaOffset = kafkaOffset;
        
        // Set partition fields
        if (event.getTimestamp() != null) {
            var dateTime = event.getTimestamp().atZone(ZoneOffset.UTC);
            record.year = dateTime.getYear();
            record.month = dateTime.getMonthValue();
            record.day = dateTime.getDayOfMonth();
        } else {
            var now = Instant.now().atZone(ZoneOffset.UTC);
            record.year = now.getYear();
            record.month = now.getMonthValue();
            record.day = now.getDayOfMonth();
        }
        
        return record;
    }
    
    // Getters
    public String getEventId() { return eventId; }
    public Instant getEventTimestamp() { return eventTimestamp; }
    public String getUserId() { return userId; }
    public String getApiKeyId() { return apiKeyId; }
    public String getClientIp() { return clientIp; }
    public String getUserAgent() { return userAgent; }
    public String getConversionType() { return conversionType; }
    public Integer getInputValue() { return inputValue; }
    public String getOutputValue() { return outputValue; }
    public Integer getMinRange() { return minRange; }
    public Integer getMaxRange() { return maxRange; }
    public String getRangeResults() { return rangeResults; }
    public String getErrorType() { return errorType; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getIngestedAt() { return ingestedAt; }
    public boolean isLateArrival() { return isLateArrival; }
    public int getKafkaPartition() { return kafkaPartition; }
    public long getKafkaOffset() { return kafkaOffset; }
    public int getYear() { return year; }
    public int getMonth() { return month; }
    public int getDay() { return day; }
    
    @Override
    public String toString() {
        return "BronzeRecord{" +
                "eventId='" + eventId + '\'' +
                ", eventTimestamp=" + eventTimestamp +
                ", conversionType='" + conversionType + '\'' +
                ", isLateArrival=" + isLateArrival +
                ", year=" + year +
                ", month=" + month +
                ", day=" + day +
                '}';
    }
}

