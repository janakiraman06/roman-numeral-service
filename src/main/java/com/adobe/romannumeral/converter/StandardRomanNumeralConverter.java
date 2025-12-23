package com.adobe.romannumeral.converter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard implementation of Roman numeral conversion using a pre-computed cache.
 * 
 * <p>This implementation pre-computes all Roman numeral conversions (1-3999) at
 * application startup, trading O(n) startup time and O(n) space for O(1) runtime
 * lookups. This is optimal for a service that will handle many conversion requests.</p>
 * 
 * <h2>Algorithm:</h2>
 * <p>Uses the greedy/subtraction method with value-symbol mapping. The algorithm
 * iterates through values in descending order, appending symbols and subtracting
 * values until the number reaches zero.</p>
 * 
 * <h2>Reference:</h2>
 * <p>Based on the Roman numeral specification from 
 * <a href="https://en.wikipedia.org/wiki/Roman_numerals">Wikipedia</a>.</p>
 * 
 * <h2>Complexity Analysis:</h2>
 * <ul>
 *   <li><b>Initialization:</b> O(n) time, O(n) space where n = 3999</li>
 *   <li><b>Runtime lookup:</b> O(1) time, O(1) space</li>
 *   <li><b>Memory usage:</b> ~40KB for 3999 cached strings</li>
 * </ul>
 * 
 * <h2>Thread Safety:</h2>
 * <p>This class is thread-safe. The cache is immutable after initialization,
 * allowing concurrent read access without synchronization.</p>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@Component
public class StandardRomanNumeralConverter implements RomanNumeralConverter {

    private static final Logger logger = LoggerFactory.getLogger(StandardRomanNumeralConverter.class);

    /**
     * Roman numeral values in descending order.
     * Includes subtractive combinations (e.g., 4=IV, 9=IX, 40=XL, etc.)
     */
    private static final int[] VALUES = {
        1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1
    };

    /**
     * Roman numeral symbols corresponding to VALUES array.
     * Each symbol maps to the value at the same index.
     */
    private static final String[] SYMBOLS = {
        "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"
    };

    /**
     * Pre-computed cache of all Roman numeral conversions.
     * Key: integer (1-3999), Value: Roman numeral string
     * Immutable after initialization for thread safety.
     */
    private Map<Integer, String> cache;

    /**
     * Initializes the pre-computed cache after bean construction.
     * This method is called automatically by Spring after dependency injection.
     */
    @PostConstruct
    public void initialize() {
        long startTime = System.currentTimeMillis();
        
        Map<Integer, String> tempCache = new HashMap<>(MAX_VALUE);
        
        for (int i = MIN_VALUE; i <= MAX_VALUE; i++) {
            tempCache.put(i, computeRomanNumeral(i));
        }
        
        // Make cache immutable for thread safety
        this.cache = Collections.unmodifiableMap(tempCache);
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Pre-computed {} Roman numeral conversions in {}ms", MAX_VALUE, duration);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation uses a pre-computed cache for O(1) lookup.
     * The cache is populated at startup with all valid conversions.</p>
     * 
     * @throws IllegalArgumentException if number is outside range 1-3999
     */
    @Override
    public String convert(int number) {
        if (!isValidRange(number)) {
            throw new IllegalArgumentException(
                String.format("Number must be between %d and %d, got: %d", 
                    MIN_VALUE, MAX_VALUE, number));
        }
        
        String result = cache.get(number);
        
        // Defensive check - should never happen with properly initialized cache
        if (result == null) {
            logger.error("Cache miss for number: {}. This should not happen.", number);
            throw new IllegalStateException("Cache not properly initialized for: " + number);
        }
        
        return result;
    }

    /**
     * Computes the Roman numeral representation using the greedy algorithm.
     * 
     * <p><b>Algorithm:</b></p>
     * <ol>
     *   <li>Start with the largest value (1000 = M)</li>
     *   <li>While current value can be subtracted from number:
     *     <ul>
     *       <li>Append corresponding symbol to result</li>
     *       <li>Subtract value from number</li>
     *     </ul>
     *   </li>
     *   <li>Move to next smaller value</li>
     *   <li>Repeat until number becomes 0</li>
     * </ol>
     * 
     * <p><b>Complexity:</b> O(1) - Maximum 13 iterations regardless of input</p>
     * 
     * @param number the integer to convert (assumed to be in valid range)
     * @return the Roman numeral representation
     */
    private String computeRomanNumeral(int number) {
        StringBuilder result = new StringBuilder();
        int remaining = number;
        
        // Iterate through value-symbol pairs in descending order
        for (int i = 0; i < VALUES.length && remaining > 0; i++) {
            // While current value can be subtracted, append symbol
            while (remaining >= VALUES[i]) {
                result.append(SYMBOLS[i]);
                remaining -= VALUES[i];
            }
        }
        
        return result.toString();
    }

    /**
     * Returns the size of the pre-computed cache.
     * Useful for monitoring and debugging.
     * 
     * @return the number of entries in the cache
     */
    public int getCacheSize() {
        return cache != null ? cache.size() : 0;
    }
}

