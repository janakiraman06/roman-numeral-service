package com.adobe.romannumeral.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response model for range-based Roman numeral conversions.
 * 
 * <p>This record represents the JSON response for the range conversion endpoint.
 * It contains an array of conversion results in ascending order.</p>
 * 
 * <h2>Response Format:</h2>
 * <pre>
 * {
 *     "conversions": [
 *         {"input": "1", "output": "I"},
 *         {"input": "2", "output": "II"},
 *         {"input": "3", "output": "III"}
 *     ]
 * }
 * </pre>
 * 
 * <h2>Specification Requirements:</h2>
 * <ul>
 *   <li>Array field must be named "conversions" (plural)</li>
 *   <li>Results must be in ascending order by input value</li>
 *   <li>Each element contains input/output string pairs</li>
 * </ul>
 * 
 * <h2>Design Notes:</h2>
 * <ul>
 *   <li>Uses Java record for immutability</li>
 *   <li>List is expected to be unmodifiable for thread safety</li>
 *   <li>Results are sorted during parallel processing</li>
 * </ul>
 * 
 * @param conversions list of conversion results in ascending order
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@Schema(description = "Result of a range-based Roman numeral conversion")
public record RangeConversionResult(
    
    @Schema(
        description = "Array of conversion results in ascending order by input value"
    )
    List<ConversionResult> conversions
    
) {
    /**
     * Factory method to create a RangeConversionResult from a list of conversions.
     * 
     * @param conversions the list of conversion results (should be in ascending order)
     * @return a new RangeConversionResult instance
     */
    public static RangeConversionResult of(List<ConversionResult> conversions) {
        return new RangeConversionResult(List.copyOf(conversions)); // Defensive copy
    }
    
    /**
     * Returns the number of conversions in this result.
     * 
     * @return the count of conversions
     */
    public int size() {
        return conversions != null ? conversions.size() : 0;
    }
}

