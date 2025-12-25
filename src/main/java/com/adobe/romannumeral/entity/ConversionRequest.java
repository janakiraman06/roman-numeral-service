package com.adobe.romannumeral.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Conversion Request entity capturing all API conversion events.
 *
 * <p>This is the primary fact table for analytics in the data warehouse.
 * Each row represents a single API call, whether for single number
 * conversion or range conversion.</p>
 *
 * <p>Data Engineering Considerations:
 * <ul>
 *   <li>Grain: One row per API request (finest grain for flexibility)</li>
 *   <li>Designed for append-only writes (immutable facts)</li>
 *   <li>Includes denormalized fields for query optimization</li>
 *   <li>Partitionable by timestamp for time-series analysis</li>
 *   <li>Supports late-arriving data via event_time vs processing_time</li>
 * </ul>
 * </p>
 *
 * <p>This entity feeds the Bronze layer in the Medallion architecture,
 * with downstream Silver and Gold transformations handled by Spark.</p>
 */
@Entity
@Table(name = "conversion_requests", indexes = {
    @Index(name = "idx_conversion_requests_timestamp", columnList = "requestTimestamp"),
    @Index(name = "idx_conversion_requests_user_id", columnList = "user_id"),
    @Index(name = "idx_conversion_requests_type", columnList = "requestType"),
    @Index(name = "idx_conversion_requests_input", columnList = "inputNumber")
})
public class ConversionRequest {

    /**
     * Type of conversion request.
     */
    public enum RequestType {
        /** Single number conversion: /romannumeral?query=42 */
        SINGLE,
        /** Range conversion: /romannumeral?min=1&max=100 */
        RANGE
    }

    /**
     * Request status for tracking success/failure.
     */
    public enum RequestStatus {
        SUCCESS,
        ERROR_VALIDATION,
        ERROR_RATE_LIMIT,
        ERROR_AUTH,
        ERROR_INTERNAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id")
    private ApiKey apiKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.SUCCESS;

    // Single conversion fields
    @Column
    private Integer inputNumber;

    @Column(length = 20)
    private String outputRoman;

    // Range conversion fields
    @Column
    private Integer rangeMin;

    @Column
    private Integer rangeMax;

    @Column
    private Integer resultCount;

    // Pagination fields
    @Column
    private Integer pageOffset;

    @Column
    private Integer pageLimit;

    // Performance metrics
    @Column(nullable = false)
    private Long responseTimeNanos;

    // Request metadata
    @Column(length = 45)
    private String clientIp;

    @Column(length = 500)
    private String userAgent;

    @Column(length = 100)
    private String correlationId;

    // Timestamps
    /**
     * When the request was received (event time).
     */
    @Column(nullable = false, updatable = false)
    private Instant requestTimestamp;

    /**
     * When this record was persisted (processing time).
     * Used for late-arriving data detection.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Default constructor for JPA
    protected ConversionRequest() {
    }

    /**
     * Creates a single conversion request.
     */
    public static ConversionRequest single(Integer input, String output, long responseTimeNanos) {
        ConversionRequest request = new ConversionRequest();
        request.requestType = RequestType.SINGLE;
        request.inputNumber = input;
        request.outputRoman = output;
        request.responseTimeNanos = responseTimeNanos;
        request.requestTimestamp = Instant.now();
        return request;
    }

    /**
     * Creates a range conversion request.
     */
    public static ConversionRequest range(Integer min, Integer max, int resultCount, 
            Integer offset, Integer limit, long responseTimeNanos) {
        ConversionRequest request = new ConversionRequest();
        request.requestType = RequestType.RANGE;
        request.rangeMin = min;
        request.rangeMax = max;
        request.resultCount = resultCount;
        request.pageOffset = offset;
        request.pageLimit = limit;
        request.responseTimeNanos = responseTimeNanos;
        request.requestTimestamp = Instant.now();
        return request;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.requestTimestamp == null) {
            this.requestTimestamp = this.createdAt;
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public void setApiKey(ApiKey apiKey) {
        this.apiKey = apiKey;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public Integer getInputNumber() {
        return inputNumber;
    }

    public String getOutputRoman() {
        return outputRoman;
    }

    public Integer getRangeMin() {
        return rangeMin;
    }

    public Integer getRangeMax() {
        return rangeMax;
    }

    public Integer getResultCount() {
        return resultCount;
    }

    public Integer getPageOffset() {
        return pageOffset;
    }

    public Integer getPageLimit() {
        return pageLimit;
    }

    public Long getResponseTimeNanos() {
        return responseTimeNanos;
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

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Instant getRequestTimestamp() {
        return requestTimestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Calculates response time in milliseconds.
     */
    public double getResponseTimeMs() {
        return responseTimeNanos / 1_000_000.0;
    }

    /**
     * Calculates the range size for range requests.
     */
    public Integer getRangeSize() {
        if (requestType == RequestType.RANGE && rangeMin != null && rangeMax != null) {
            return rangeMax - rangeMin + 1;
        }
        return null;
    }

    @Override
    public String toString() {
        if (requestType == RequestType.SINGLE) {
            return String.format("ConversionRequest{id=%d, type=SINGLE, input=%d, output='%s', time=%.2fms}",
                    id, inputNumber, outputRoman, getResponseTimeMs());
        } else {
            return String.format("ConversionRequest{id=%d, type=RANGE, range=[%d-%d], count=%d, time=%.2fms}",
                    id, rangeMin, rangeMax, resultCount, getResponseTimeMs());
        }
    }
}

