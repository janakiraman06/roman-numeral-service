package com.adobe.romannumeral.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response model for a single Roman numeral conversion.
 * 
 * <p>This record represents the JSON response for the single conversion endpoint.
 * Both fields are strings as per the API specification.</p>
 * 
 * <h2>Response Format:</h2>
 * <pre>
 * {
 *     "input": "42",
 *     "output": "XLII"
 * }
 * </pre>
 * 
 * <h2>Design Notes:</h2>
 * <ul>
 *   <li>Uses Java record for immutability and thread safety</li>
 *   <li>Both fields are String type as required by the specification</li>
 *   <li>Input is the original number as a string, not an integer</li>
 * </ul>
 * 
 * @param input  the original integer value as a string (e.g., "42")
 * @param output the Roman numeral representation (e.g., "XLII")
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@Schema(description = "Result of a single integer to Roman numeral conversion")
public record ConversionResult(
    
    @Schema(
        description = "The original integer value as a string",
        example = "42"
    )
    String input,
    
    @Schema(
        description = "The Roman numeral representation",
        example = "XLII"
    )
    String output
    
) {
    /**
     * Factory method to create a ConversionResult from an integer and its Roman numeral.
     * 
     * @param number      the integer that was converted
     * @param romanNumeral the Roman numeral result
     * @return a new ConversionResult instance
     */
    public static ConversionResult of(int number, String romanNumeral) {
        return new ConversionResult(String.valueOf(number), romanNumeral);
    }
}

