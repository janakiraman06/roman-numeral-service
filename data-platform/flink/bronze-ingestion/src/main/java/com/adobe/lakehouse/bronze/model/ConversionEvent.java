package com.adobe.lakehouse.bronze.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a Roman Numeral conversion event from Kafka.
 * 
 * <p>This POJO matches the JSON schema produced by the Spring Boot application's
 * {@code KafkaProducerService}. It implements {@link Serializable} for Flink state management.</p>
 * 
 * <h3>Schema:</h3>
 * <pre>
 * {
 *   "eventId": "uuid",
 *   "timestamp": "ISO-8601",
 *   "userId": "uuid or null",
 *   "apiKeyId": "uuid or null",
 *   "clientIp": "string",
 *   "userAgent": "string",
 *   "conversionType": "single|range|error",
 *   "inputValue": number or null,
 *   "outputValue": "string or null",
 *   "minRange": number or null,
 *   "maxRange": number or null,
 *   "rangeResults": "string or null",
 *   "errorType": "string or null",
 *   "errorMessage": "string or null"
 * }
 * </pre>
 * 
 * @author Roman Numeral Service Data Platform
 * @version 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConversionEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    // Event identification
    @JsonProperty("eventId")
    private String eventId;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    // User context (nullable for unauthenticated requests)
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("apiKeyId")
    private String apiKeyId;
    
    // Request metadata
    @JsonProperty("clientIp")
    private String clientIp;
    
    @JsonProperty("userAgent")
    private String userAgent;
    
    // Conversion details
    @JsonProperty("conversionType")
    private String conversionType;
    
    @JsonProperty("inputValue")
    private Integer inputValue;
    
    @JsonProperty("outputValue")
    private String outputValue;
    
    @JsonProperty("minRange")
    private Integer minRange;
    
    @JsonProperty("maxRange")
    private Integer maxRange;
    
    @JsonProperty("rangeResults")
    private String rangeResults;
    
    // Error details (for error events)
    @JsonProperty("errorType")
    private String errorType;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    // Default constructor for Jackson
    public ConversionEvent() {
    }
    
    /**
     * Parses a JSON string into a ConversionEvent.
     * 
     * @param json The JSON string to parse
     * @return The parsed ConversionEvent
     * @throws RuntimeException if parsing fails
     */
    public static ConversionEvent fromJson(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, ConversionEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse ConversionEvent from JSON: " + json, e);
        }
    }
    
    /**
     * Converts this event to a JSON string.
     * 
     * @return JSON representation
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ConversionEvent to JSON", e);
        }
    }
    
    /**
     * Extracts the event timestamp in milliseconds for watermark assignment.
     * 
     * @return Event timestamp in epoch milliseconds
     */
    public long getTimestampMillis() {
        return timestamp != null ? timestamp.toEpochMilli() : System.currentTimeMillis();
    }
    
    // Getters and Setters
    public String getEventId() {
        return eventId;
    }
    
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getApiKeyId() {
        return apiKeyId;
    }
    
    public void setApiKeyId(String apiKeyId) {
        this.apiKeyId = apiKeyId;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public String getConversionType() {
        return conversionType;
    }
    
    public void setConversionType(String conversionType) {
        this.conversionType = conversionType;
    }
    
    public Integer getInputValue() {
        return inputValue;
    }
    
    public void setInputValue(Integer inputValue) {
        this.inputValue = inputValue;
    }
    
    public String getOutputValue() {
        return outputValue;
    }
    
    public void setOutputValue(String outputValue) {
        this.outputValue = outputValue;
    }
    
    public Integer getMinRange() {
        return minRange;
    }
    
    public void setMinRange(Integer minRange) {
        this.minRange = minRange;
    }
    
    public Integer getMaxRange() {
        return maxRange;
    }
    
    public void setMaxRange(Integer maxRange) {
        this.maxRange = maxRange;
    }
    
    public String getRangeResults() {
        return rangeResults;
    }
    
    public void setRangeResults(String rangeResults) {
        this.rangeResults = rangeResults;
    }
    
    public String getErrorType() {
        return errorType;
    }
    
    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversionEvent that = (ConversionEvent) o;
        return Objects.equals(eventId, that.eventId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
    
    @Override
    public String toString() {
        return "ConversionEvent{" +
                "eventId='" + eventId + '\'' +
                ", timestamp=" + timestamp +
                ", conversionType='" + conversionType + '\'' +
                ", inputValue=" + inputValue +
                ", outputValue='" + outputValue + '\'' +
                '}';
    }
}

