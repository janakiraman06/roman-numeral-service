package com.adobe.romannumeral.service;

import com.adobe.romannumeral.converter.RomanNumeralConverter;
import com.adobe.romannumeral.model.ConversionResult;
import com.adobe.romannumeral.model.RangeConversionResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Parallel processor for range-based Roman numeral conversions.
 * 
 * <p>This component uses Java 8 Parallel Streams to process range conversions
 * in parallel, providing efficient handling of ranges with minimal overhead.</p>
 * 
 * <h2>Hybrid Threading Strategy (ADR-008):</h2>
 * <p>We use a layered approach to threading:</p>
 * <ul>
 *   <li><b>HTTP Layer:</b> Spring Virtual Threads for I/O-bound request handling</li>
 *   <li><b>Processing Layer:</b> Parallel Streams for CPU-bound cache lookups</li>
 * </ul>
 * 
 * <h2>Why Parallel Streams over Virtual Threads for Processing?</h2>
 * <p>Benchmarks showed virtual threads have ~10x more overhead than parallel streams
 * for CPU-bound operations like cache lookups. Virtual threads excel at I/O-bound
 * waiting, but our O(1) array lookups complete in nanoseconds - the thread scheduling
 * overhead dominates the actual work time.</p>
 * 
 * <h2>Why Parallel Processing Here?</h2>
 * <ul>
 *   <li>Meets specification requirement for multithreading</li>
 *   <li>Parallel streams use ForkJoinPool efficiently</li>
 *   <li>Lower overhead than virtual threads for this workload</li>
 * </ul>
 * 
 * <h2>Complexity:</h2>
 * <ul>
 *   <li>Time: O(n/p) where n = range size, p = parallelism</li>
 *   <li>Space: O(n) for storing results</li>
 * </ul>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 2.0.0
 * @see <a href="../../../docs/adr/008-hybrid-threading-strategy.md">ADR-008: Hybrid Threading Strategy</a>
 */
@Component
public class ParallelRangeProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ParallelRangeProcessor.class);
    
    /**
     * Maximum allowed range size to prevent resource exhaustion.
     * 
     * <p>This protects against requests like ?min=1&max=3999 which would
     * process 3999 items. Value chosen to balance usability and resource protection.</p>
     */
    private static final int MAX_RANGE_SIZE = 1000;
    
    private final RomanNumeralConverter converter;
    
    // Micrometer metrics for observability
    private final Timer rangeProcessingTimer;
    private final Counter rangeRequestsCounter;
    private final DistributionSummary rangeSizeDistribution;

    /**
     * Constructs the processor with the required converter and metrics registry.
     * 
     * @param converter the Roman numeral converter to use
     * @param meterRegistry the Micrometer registry for metrics
     */
    public ParallelRangeProcessor(RomanNumeralConverter converter, MeterRegistry meterRegistry) {
        this.converter = converter;
        
        // Timer for range processing duration
        this.rangeProcessingTimer = Timer.builder("range.processing.time")
            .description("Time taken to process range conversion requests")
            .tag("processor", "parallel-stream")
            .register(meterRegistry);
        
        // Counter for range requests
        this.rangeRequestsCounter = Counter.builder("range.requests.total")
            .description("Total number of range conversion requests")
            .register(meterRegistry);
        
        // Distribution summary for range sizes
        this.rangeSizeDistribution = DistributionSummary.builder("range.processing.size")
            .description("Distribution of range sizes requested")
            .baseUnit("items")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        
        logger.info("ParallelRangeProcessor initialized with parallel streams and Micrometer metrics");
    }

    /**
     * Processes a range of integers in parallel and returns sorted results.
     * 
     * <p>This method uses Java 8 Parallel Streams with ForkJoinPool to process
     * range conversions efficiently. Parallel streams have lower overhead than
     * virtual threads for CPU-bound O(1) cache lookups.</p>
     * 
     * <h3>Algorithm:</h3>
     * <ol>
     *   <li>Create parallel IntStream for the range</li>
     *   <li>Map each integer to ConversionResult in parallel</li>
     *   <li>Sort results by input value (ascending)</li>
     *   <li>Collect to immutable list</li>
     * </ol>
     * 
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     * @return RangeConversionResult with sorted conversions
     * @throws IllegalArgumentException if range is invalid
     */
    public RangeConversionResult processRange(int min, int max) {
        validateRange(min, max);
        
        int rangeSize = max - min + 1;
        
        // Record metrics
        rangeRequestsCounter.increment();
        rangeSizeDistribution.record(rangeSize);
        
        logger.debug("Processing range [{}, {}] with {} values using parallel streams", 
            min, max, rangeSize);

        long startTime = System.nanoTime();
        
        // Use parallel stream for efficient multithreaded processing
        // ForkJoinPool handles work-stealing for optimal CPU utilization
        List<ConversionResult> results = IntStream.rangeClosed(min, max)
            .parallel()
            .mapToObj(this::convertNumber)
            .sorted(Comparator.comparingInt(r -> Integer.parseInt(r.input())))
            .toList();
        
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        
        // Record processing time
        rangeProcessingTimer.record(java.time.Duration.ofNanos(durationNanos));
        
        logger.info("Processed {} conversions in {}ms using parallel streams", 
            rangeSize, 
            durationNanos / 1_000_000);
        
        return RangeConversionResult.of(results);
    }

    /**
     * Validates the range parameters according to specification.
     * 
     * <h3>Validation Rules:</h3>
     * <ul>
     *   <li>min must be in valid range (1-3999)</li>
     *   <li>max must be in valid range (1-3999)</li>
     *   <li>min must be strictly less than max</li>
     *   <li>Range size must not exceed maxRangeSize (resource protection)</li>
     * </ul>
     * 
     * @param min the minimum value
     * @param max the maximum value
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRange(int min, int max) {
        if (!converter.isValidRange(min)) {
            throw new IllegalArgumentException(
                String.format("min value must be between %d and %d, got: %d",
                    RomanNumeralConverter.MIN_VALUE, RomanNumeralConverter.MAX_VALUE, min));
        }
        
        if (!converter.isValidRange(max)) {
            throw new IllegalArgumentException(
                String.format("max value must be between %d and %d, got: %d",
                    RomanNumeralConverter.MIN_VALUE, RomanNumeralConverter.MAX_VALUE, max));
        }
        
        if (min >= max) {
            throw new IllegalArgumentException(
                String.format("min (%d) must be less than max (%d)", min, max));
        }
        
        int rangeSize = max - min + 1;
        if (rangeSize > MAX_RANGE_SIZE) {
            throw new IllegalArgumentException(
                String.format("Range size (%d) exceeds maximum allowed (%d). " +
                    "Please request a smaller range.", rangeSize, MAX_RANGE_SIZE));
        }
    }

    /**
     * Converts a single number and returns the result.
     * 
     * @param number the number to convert
     * @return ConversionResult for the number
     */
    private ConversionResult convertNumber(int number) {
        String romanNumeral = converter.convert(number);
        return ConversionResult.of(number, romanNumeral);
    }

}

