package com.adobe.romannumeral.controller;

import com.adobe.romannumeral.exception.InvalidInputException;
import com.adobe.romannumeral.model.ConversionResult;
import com.adobe.romannumeral.model.PagedRangeResult;
import com.adobe.romannumeral.model.RangeConversionResult;
import com.adobe.romannumeral.service.KafkaProducerService;
import com.adobe.romannumeral.service.ParallelRangeProcessor;
import com.adobe.romannumeral.service.RomanNumeralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

/**
 * REST Controller for Roman numeral conversion endpoints.
 * 
 * <p>This controller provides the API endpoints as specified in the requirements:</p>
 * <ul>
 *   <li>Single conversion: GET /romannumeral?query={integer}</li>
 *   <li>Range conversion: GET /romannumeral?min={integer}&amp;max={integer}</li>
 * </ul>
 * 
 * <h2>Response Formats:</h2>
 * <ul>
 *   <li><b>Success:</b> JSON with input/output fields</li>
 *   <li><b>Error:</b> Plain text message</li>
 * </ul>
 * 
 * <h2>Versioning Strategy:</h2>
 * <p>Header-based versioning via Accept-Version header (defaults to v1).
 * URL matches specification exactly without version prefix.</p>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@RestController
@Tag(name = "Roman Numeral Conversion", description = "Convert integers to Roman numerals")
public class RomanNumeralController {

    private static final Logger logger = LoggerFactory.getLogger(RomanNumeralController.class);
    private static final String CORRELATION_ID = "correlationId";

    private final RomanNumeralService romanNumeralService;
    private final Optional<KafkaProducerService> kafkaProducerService;

    /**
     * Constructs the controller with the required services.
     * 
     * @param romanNumeralService the service for Roman numeral conversions
     * @param kafkaProducerService optional Kafka producer (only present when app.kafka.enabled=true)
     */
    public RomanNumeralController(
            RomanNumeralService romanNumeralService,
            @Autowired(required = false) KafkaProducerService kafkaProducerService) {
        this.romanNumeralService = romanNumeralService;
        this.kafkaProducerService = Optional.ofNullable(kafkaProducerService);
        
        if (this.kafkaProducerService.isPresent()) {
            logger.info("KafkaProducerService injected - events will be published to Kafka");
        } else {
            logger.info("KafkaProducerService not available - Kafka publishing disabled");
        }
    }

    /**
     * Converts a single integer to a Roman numeral.
     * 
     * <h3>Endpoint:</h3>
     * <pre>GET /romannumeral?query={integer}</pre>
     * 
     * <h3>Example:</h3>
     * <pre>
     * Request:  GET /romannumeral?query=42
     * Response: {"input": "42", "output": "XLII"}
     * </pre>
     * 
     * @param query the integer to convert (1-3999)
     * @param min   optional min parameter (triggers range query if both min and max present)
     * @param max   optional max parameter (triggers range query if both min and max present)
     * @return ResponseEntity containing the conversion result as JSON
     */
    @GetMapping(value = "/romannumeral", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
        summary = "Convert integer to Roman numeral",
        description = "Converts a single integer or a range of integers to Roman numerals. " +
                      "Use 'query' for single conversion, or 'min' and 'max' for range conversion. " +
                      "Range queries support pagination with 'offset' and 'limit' parameters."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successful conversion",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(oneOf = {ConversionResult.class, RangeConversionResult.class, PagedRangeResult.class})
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid input",
            content = @Content(mediaType = "text/plain")
        )
    })
    public ResponseEntity<?> convert(
            @Parameter(description = "Integer to convert (1-3999)")
            @RequestParam(required = false) Integer query,
            
            @Parameter(description = "Minimum value for range conversion (1-3999)")
            @RequestParam(required = false) Integer min,
            
            @Parameter(description = "Maximum value for range conversion (1-3999)")
            @RequestParam(required = false) Integer max,
            
            @Parameter(description = "Starting position for pagination (0-based, default: 0)")
            @RequestParam(required = false) Integer offset,
            
            @Parameter(description = "Maximum items per page (default: 100, max: 500)")
            @RequestParam(required = false) Integer limit,
            
            HttpServletRequest request) {
        
        // Generate correlation ID for request tracing
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(CORRELATION_ID, correlationId);
        
        // Track timing for Kafka event
        long startTime = System.nanoTime();
        String clientIp = getClientIp(request);
        
        try {
            // Determine which type of request this is
            if (isRangeQuery(min, max)) {
                return handleRangeConversion(min, max, offset, limit, correlationId, clientIp, startTime);
            } else if (query != null) {
                return handleSingleConversion(query, correlationId, clientIp, startTime);
            } else {
                throw new InvalidInputException(
                    "Missing required parameter. Provide 'query' for single conversion, " +
                    "or both 'min' and 'max' for range conversion.");
            }
        } finally {
            MDC.remove(CORRELATION_ID);
        }
    }

    /**
     * Handles single integer conversion.
     * 
     * @param query the integer to convert
     * @param correlationId request correlation ID
     * @param clientIp client IP address
     * @param startTime request start time in nanos
     * @return ResponseEntity with ConversionResult
     */
    private ResponseEntity<ConversionResult> handleSingleConversion(
            int query, String correlationId, String clientIp, long startTime) {
        logger.info("Processing single conversion request for: {}", query);
        
        ConversionResult result = romanNumeralService.convertSingle(query);
        
        // Publish event to Kafka
        long responseTimeNanos = System.nanoTime() - startTime;
        kafkaProducerService.ifPresent(kafka -> 
            kafka.publishSingleConversion(
                query, 
                result.output(), 
                responseTimeNanos, 
                correlationId, 
                clientIp, 
                null,  // userId - would come from auth context
                null   // apiKeyPrefix - would come from auth context
            )
        );
        
        logger.info("Successfully converted {} to {}", result.input(), result.output());
        return ResponseEntity.ok(result);
    }

    /**
     * Handles range-based conversion with parallel processing and optional pagination.
     * 
     * <p>If offset or limit parameters are provided, returns paginated results.
     * Otherwise, returns full range results (for backward compatibility with small ranges).</p>
     * 
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     * @param offset starting position (0-based), null for full range
     * @param limit maximum items per page, null for full range
     * @param correlationId request correlation ID
     * @param clientIp client IP address
     * @param startTime request start time in nanos
     * @return ResponseEntity with RangeConversionResult or PagedRangeResult
     */
    private ResponseEntity<?> handleRangeConversion(
            Integer min, Integer max, Integer offset, Integer limit,
            String correlationId, String clientIp, long startTime) {
        // Validate that both parameters are present
        if (min == null || max == null) {
            throw new InvalidInputException(
                "Both 'min' and 'max' parameters are required for range conversion.");
        }
        
        // Check if pagination is requested
        boolean usePagination = offset != null || limit != null;
        
        // For large ranges (>500), force pagination
        int rangeSize = max - min + 1;
        if (rangeSize > ParallelRangeProcessor.MAX_PAGE_SIZE && !usePagination) {
            usePagination = true;
            logger.info("Large range ({}) detected, using pagination", rangeSize);
        }
        
        if (usePagination) {
            int effectiveOffset = offset != null ? offset : 0;
            int effectiveLimit = limit != null ? limit : ParallelRangeProcessor.DEFAULT_PAGE_SIZE;
            
            logger.info("Processing paginated range request: min={}, max={}, offset={}, limit={}", 
                min, max, effectiveOffset, effectiveLimit);
            
            PagedRangeResult result = romanNumeralService.convertRangePaginated(
                min, max, effectiveOffset, effectiveLimit);
            
            // Publish event to Kafka
            long responseTimeNanos = System.nanoTime() - startTime;
            kafkaProducerService.ifPresent(kafka -> 
                kafka.publishRangeConversion(
                    min, max, result.size(), effectiveOffset, effectiveLimit,
                    responseTimeNanos, correlationId, clientIp, null, null
                )
            );
            
            logger.info("Successfully converted page {}/{} of range [{}-{}]: {} conversions",
                result.pagination().currentPage(),
                result.pagination().totalPages(),
                min, max, result.size());
            
            return ResponseEntity.ok(result);
        } else {
            logger.info("Processing range conversion request: min={}, max={}", min, max);
            
            RangeConversionResult result = romanNumeralService.convertRange(min, max);
            
            // Publish event to Kafka
            long responseTimeNanos = System.nanoTime() - startTime;
            kafkaProducerService.ifPresent(kafka -> 
                kafka.publishRangeConversion(
                    min, max, result.size(), null, null,
                    responseTimeNanos, correlationId, clientIp, null, null
                )
            );
            
            logger.info("Successfully converted range [{}-{}]: {} conversions", 
                min, max, result.size());
            return ResponseEntity.ok(result);
        }
    }

    /**
     * Determines if this is a range query based on the presence of min/max parameters.
     * 
     * @param min the min parameter (may be null)
     * @param max the max parameter (may be null)
     * @return true if either min or max is present (indicating range intent)
     */
    private boolean isRangeQuery(Integer min, Integer max) {
        return min != null || max != null;
    }

    /**
     * Extracts the client IP address from the request.
     * Handles proxied requests by checking X-Forwarded-For header.
     * 
     * @param request the HTTP request
     * @return the client IP address
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs; the first is the client
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

