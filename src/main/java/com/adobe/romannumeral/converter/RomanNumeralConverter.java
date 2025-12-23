package com.adobe.romannumeral.converter;

/**
 * Interface for converting integers to Roman numeral representation.
 * 
 * <p>This interface follows the Strategy pattern, allowing for different
 * implementations of the Roman numeral conversion algorithm. The default
 * implementation uses a pre-computed cache for O(1) lookup performance.</p>
 * 
 * <h2>Design Principles:</h2>
 * <ul>
 *   <li><b>Interface Segregation</b>: Single focused method</li>
 *   <li><b>Dependency Inversion</b>: Clients depend on this abstraction</li>
 *   <li><b>Open/Closed</b>: New implementations can be added without modification</li>
 * </ul>
 * 
 * <h2>Supported Range:</h2>
 * <p>Valid input range is 1 to 3999 (inclusive), as per standard Roman numeral
 * representation. Values outside this range will result in an exception.</p>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 * @see <a href="https://en.wikipedia.org/wiki/Roman_numerals">Roman Numerals - Wikipedia</a>
 */
public interface RomanNumeralConverter {

    /**
     * Minimum supported value for Roman numeral conversion.
     */
    int MIN_VALUE = 1;

    /**
     * Maximum supported value for Roman numeral conversion.
     * <p>3999 is the largest number representable in standard Roman numerals
     * (MMMCMXCIX) without using vinculum (overline) notation.</p>
     */
    int MAX_VALUE = 3999;

    /**
     * Converts an integer to its Roman numeral representation.
     * 
     * <h3>Algorithm Complexity:</h3>
     * <ul>
     *   <li>Time Complexity: O(1) - Pre-computed cache lookup</li>
     *   <li>Space Complexity: O(1) - Returns cached string reference</li>
     * </ul>
     * 
     * <h3>Examples:</h3>
     * <pre>
     * convert(1)    → "I"
     * convert(4)    → "IV"
     * convert(9)    → "IX"
     * convert(42)   → "XLII"
     * convert(1994) → "MCMXCIV"
     * convert(3999) → "MMMCMXCIX"
     * </pre>
     * 
     * @param number the integer to convert (must be between 1 and 3999 inclusive)
     * @return the Roman numeral representation as a String
     * @throws IllegalArgumentException if number is outside the valid range (1-3999)
     */
    String convert(int number);

    /**
     * Checks if a number is within the valid range for Roman numeral conversion.
     * 
     * @param number the number to validate
     * @return true if the number is between MIN_VALUE and MAX_VALUE (inclusive)
     */
    default boolean isValidRange(int number) {
        return number >= MIN_VALUE && number <= MAX_VALUE;
    }
}

