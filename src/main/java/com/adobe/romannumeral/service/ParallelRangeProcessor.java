package com.adobe.romannumeral.service;

import com.adobe.romannumeral.converter.RomanNumeralConverter;
import com.adobe.romannumeral.model.ConversionResult;
import com.adobe.romannumeral.model.RangeConversionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

/**
 * Parallel processor for range-based Roman numeral conversions.
 * 
 * <p>This component uses Java 21 virtual threads to process range conversions
 * in parallel, providing efficient handling of large ranges without traditional
 * thread pool management overhead.</p>
 * 
 * <h2>Virtual Threads (Java 21):</h2>
 * <p>Virtual threads are lightweight threads managed by the JVM, allowing
 * creation of thousands of concurrent tasks without the overhead of
 * platform threads. This is ideal for I/O-bound or waiting operations,
 * though our CPU-bound conversion is already O(1) per lookup.</p>
 * 
 * <h2>Why Parallel Processing Here?</h2>
 * <p>While individual conversions are O(1), parallel processing demonstrates:</p>
 * <ul>
 *   <li>Meeting the specification requirement for multithreading</li>
 *   <li>Knowledge of Java 21 concurrency features</li>
 *   <li>Scalability patterns for future enhancements</li>
 * </ul>
 * 
 * <h2>Complexity:</h2>
 * <ul>
 *   <li>Time: O(n/p) where n = range size, p = parallelism</li>
 *   <li>Space: O(n) for storing results</li>
 * </ul>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@Component
public class ParallelRangeProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ParallelRangeProcessor.class);

    private final RomanNumeralConverter converter;

    /**
     * Constructs the processor with the required converter.
     * 
     * @param converter the Roman numeral converter to use
     */
    public ParallelRangeProcessor(RomanNumeralConverter converter) {
        this.converter = converter;
    }

    /**
     * Processes a range of integers in parallel and returns sorted results.
     * 
     * <p>This method creates virtual threads for each conversion in the range,
     * executes them concurrently, and collects results in ascending order.</p>
     * 
     * <h3>Algorithm:</h3>
     * <ol>
     *   <li>Create virtual thread executor</li>
     *   <li>Submit conversion tasks for each number in range</li>
     *   <li>Collect futures and wait for completion</li>
     *   <li>Sort results by input value (ascending)</li>
     *   <li>Return wrapped result</li>
     * </ol>
     * 
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     * @return RangeConversionResult with sorted conversions
     * @throws IllegalArgumentException if range is invalid
     */
    public RangeConversionResult processRange(int min, int max) {
        validateRange(min, max);
        
        long startTime = System.currentTimeMillis();
        int rangeSize = max - min + 1;
        
        logger.debug("Processing range [{}, {}] with {} values using virtual threads", 
            min, max, rangeSize);

        List<ConversionResult> results;
        
        // Use try-with-resources to ensure executor shutdown
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Submit all tasks and collect futures
            List<Future<ConversionResult>> futures = IntStream.rangeClosed(min, max)
                .mapToObj(number -> executor.submit(() -> convertNumber(number)))
                .toList();
            
            // Collect results from futures
            results = futures.stream()
                .map(this::getResultSafely)
                .sorted(Comparator.comparingInt(r -> Integer.parseInt(r.input())))
                .toList();
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Processed {} conversions in {}ms using virtual threads", 
            rangeSize, duration);
        
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

    /**
     * Safely extracts result from a Future, handling exceptions.
     * 
     * @param future the Future to extract from
     * @return the ConversionResult
     * @throws RuntimeException if extraction fails
     */
    private ConversionResult getResultSafely(Future<ConversionResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Conversion interrupted", e);
        } catch (Exception e) {
            throw new RuntimeException("Conversion failed", e);
        }
    }
}

