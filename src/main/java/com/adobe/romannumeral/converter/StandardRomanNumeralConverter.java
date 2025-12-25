package com.adobe.romannumeral.converter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Standard implementation of Roman numeral conversion using a pre-computed array cache.
 * 
 * <p>This implementation pre-computes all Roman numeral conversions (1-3999) at
 * application startup, trading O(n) startup time and O(n) space for O(1) runtime
 * lookups. This is optimal for a service that will handle many conversion requests.</p>
 * 
 * <h2>Cache Implementation: Array vs HashMap</h2>
 * <p>We use a primitive array instead of HashMap for the following reasons:</p>
 * <ul>
 *   <li><b>Direct indexing:</b> Array access is O(1) with no hash computation</li>
 *   <li><b>Better cache locality:</b> Contiguous memory improves CPU cache performance</li>
 *   <li><b>No boxing overhead:</b> Direct int indexing vs Integer autoboxing</li>
 *   <li><b>Lower memory footprint:</b> No Entry objects, load factor overhead</li>
 * </ul>
 * <p>See ADR-007 for detailed decision rationale and benchmarks.</p>
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
 *   <li><b>Runtime lookup:</b> O(1) time with direct array indexing</li>
 *   <li><b>Memory usage:</b> ~32KB for 4000-element String array</li>
 * </ul>
 * 
 * <h2>Thread Safety:</h2>
 * <p>This class is thread-safe. The cache array is populated once at initialization
 * and never modified, allowing concurrent read access without synchronization.</p>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 2.0.0
 * @see <a href="../../../docs/adr/007-array-cache-optimization.md">ADR-007: Array Cache Optimization</a>
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
     * Pre-computed cache of all Roman numeral conversions using array for O(1) indexed access.
     * Index corresponds directly to the integer value (index 1 = "I", index 2 = "II", etc.)
     * Index 0 is unused (Roman numerals start from 1).
     * 
     * <p>Array chosen over HashMap for:</p>
     * <ul>
     *   <li>Direct O(1) indexing without hash computation</li>
     *   <li>Better CPU cache locality (contiguous memory)</li>
     *   <li>No Integer autoboxing overhead</li>
     *   <li>~20% lower memory footprint</li>
     * </ul>
     */
    private String[] cache;

    /**
     * Initializes the pre-computed array cache after bean construction.
     * This method is called automatically by Spring after dependency injection.
     * 
     * <p>The array is sized to MAX_VALUE + 1 to allow direct indexing where
     * cache[n] returns the Roman numeral for integer n. Index 0 is unused.</p>
     */
    @PostConstruct
    public void initialize() {
        long startTime = System.currentTimeMillis();
        
        // Array size = MAX_VALUE + 1 for direct indexing (index 0 unused)
        this.cache = new String[MAX_VALUE + 1];
        
        for (int i = MIN_VALUE; i <= MAX_VALUE; i++) {
            cache[i] = computeRomanNumeral(i);
        }
        
        long duration = System.currentTimeMillis() - startTime;
        logger.info("Pre-computed {} Roman numeral conversions in {}ms using array cache", MAX_VALUE, duration);
    }

    /**
     * {@inheritDoc}
     * 
     * <p>This implementation uses a pre-computed array cache for O(1) lookup
     * with direct indexing. No hash computation or autoboxing overhead.</p>
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
        
        // Direct array access - O(1) with no hash computation
        String result = cache[number];
        
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
     * @return the number of entries in the cache (excludes unused index 0)
     */
    public int getCacheSize() {
        // Array length - 1 because index 0 is unused
        return cache != null ? cache.length - 1 : 0;
    }
}

