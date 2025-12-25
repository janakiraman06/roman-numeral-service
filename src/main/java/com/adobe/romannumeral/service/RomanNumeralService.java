package com.adobe.romannumeral.service;

import com.adobe.romannumeral.converter.RomanNumeralConverter;
import com.adobe.romannumeral.model.ConversionResult;
import com.adobe.romannumeral.model.PagedRangeResult;
import com.adobe.romannumeral.model.RangeConversionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service layer for Roman numeral conversion operations.
 * 
 * <p>This service provides high-level operations for converting integers to
 * Roman numerals, including single conversions and parallel range conversions.</p>
 * 
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Single integer to Roman numeral conversion</li>
 *   <li>Range-based parallel conversion with result ordering</li>
 *   <li>Logging and metrics integration</li>
 * </ul>
 * 
 * <h2>Design Principles:</h2>
 * <ul>
 *   <li><b>Single Responsibility</b>: Orchestrates conversion operations</li>
 *   <li><b>Dependency Injection</b>: Converter and processor injected</li>
 * </ul>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@Service
public class RomanNumeralService {

    private static final Logger logger = LoggerFactory.getLogger(RomanNumeralService.class);

    private final RomanNumeralConverter converter;
    private final ParallelRangeProcessor rangeProcessor;

    /**
     * Constructs the service with required dependencies.
     * 
     * @param converter      the Roman numeral converter implementation
     * @param rangeProcessor the parallel range processor
     */
    public RomanNumeralService(RomanNumeralConverter converter, 
                                ParallelRangeProcessor rangeProcessor) {
        this.converter = converter;
        this.rangeProcessor = rangeProcessor;
    }

    /**
     * Converts a single integer to its Roman numeral representation.
     * 
     * <p>This method delegates to the converter and wraps the result
     * in a ConversionResult object for API response.</p>
     * 
     * @param number the integer to convert (1-3999)
     * @return ConversionResult containing input and output strings
     * @throws IllegalArgumentException if number is outside valid range
     */
    public ConversionResult convertSingle(int number) {
        logger.debug("Converting single value: {}", number);
        
        String romanNumeral = converter.convert(number);
        
        logger.debug("Converted {} to {}", number, romanNumeral);
        return ConversionResult.of(number, romanNumeral);
    }

    /**
     * Converts a range of integers to Roman numerals in parallel.
     * 
     * <p>This method uses the parallel range processor to convert multiple
     * values concurrently using virtual threads, then assembles the results
     * in ascending order.</p>
     * 
     * <h3>Requirements (from spec):</h3>
     * <ul>
     *   <li>Both min and max must be provided</li>
     *   <li>min must be less than max (strict inequality)</li>
     *   <li>Both must be in range 1-3999</li>
     *   <li>Results must be in ascending order</li>
     * </ul>
     * 
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     * @return RangeConversionResult containing array of conversions
     * @throws IllegalArgumentException if parameters are invalid
     */
    public RangeConversionResult convertRange(int min, int max) {
        logger.debug("Converting range: {} to {}", min, max);
        
        RangeConversionResult result = rangeProcessor.processRange(min, max);
        
        logger.debug("Converted range of {} values", result.size());
        return result;
    }

    /**
     * Converts a range of integers with pagination support.
     * 
     * <p>This method allows clients to request large ranges (up to 1-3999) 
     * with pagination, returning a subset of results with navigation metadata.</p>
     * 
     * <h3>Pagination Parameters:</h3>
     * <ul>
     *   <li><b>offset:</b> Starting position (0-based), default 0</li>
     *   <li><b>limit:</b> Maximum items to return, default 100, max 500</li>
     * </ul>
     * 
     * @param min the minimum value (inclusive)
     * @param max the maximum value (inclusive)
     * @param offset starting position (0-based)
     * @param limit maximum items to return per page
     * @return PagedRangeResult with conversions and pagination metadata
     * @throws IllegalArgumentException if parameters are invalid
     * @see <a href="../../../docs/adr/009-pagination.md">ADR-009: Pagination Strategy</a>
     */
    public PagedRangeResult convertRangePaginated(int min, int max, int offset, int limit) {
        logger.debug("Converting paginated range: {} to {} (offset={}, limit={})", min, max, offset, limit);
        
        PagedRangeResult result = rangeProcessor.processRangePaginated(min, max, offset, limit);
        
        logger.debug("Converted page with {} values (total: {})", 
            result.size(), result.pagination().totalItems());
        return result;
    }

    /**
     * Checks if a number is within the valid conversion range.
     * 
     * @param number the number to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidNumber(int number) {
        return converter.isValidRange(number);
    }
}

