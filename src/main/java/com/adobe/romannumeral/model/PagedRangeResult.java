package com.adobe.romannumeral.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Paginated response model for range-based Roman numeral conversions.
 * 
 * <p>This record extends the basic range conversion with pagination metadata,
 * allowing clients to request subsets of large ranges efficiently.</p>
 * 
 * <h2>Response Format:</h2>
 * <pre>
 * {
 *     "conversions": [
 *         {"input": "101", "output": "CI"},
 *         {"input": "102", "output": "CII"}
 *     ],
 *     "pagination": {
 *         "offset": 100,
 *         "limit": 100,
 *         "totalItems": 3999,
 *         "totalPages": 40,
 *         "currentPage": 2,
 *         "hasNext": true,
 *         "hasPrevious": true
 *     }
 * }
 * </pre>
 * 
 * <h2>Pagination Strategy:</h2>
 * <p>Uses offset-based pagination which is simple and works well for our use case.
 * See ADR-009 for decision rationale.</p>
 * 
 * @param conversions list of conversion results for the current page
 * @param pagination metadata about the pagination state
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 * @see <a href="../../../docs/adr/009-pagination.md">ADR-009: Pagination Strategy</a>
 */
@Schema(description = "Paginated result of a range-based Roman numeral conversion")
public record PagedRangeResult(
    
    @Schema(description = "Array of conversion results for the current page")
    List<ConversionResult> conversions,
    
    @Schema(description = "Pagination metadata")
    PaginationInfo pagination
    
) {
    
    /**
     * Pagination metadata record.
     * 
     * @param offset starting position (0-based)
     * @param limit maximum items per page
     * @param totalItems total number of items in the full range
     * @param totalPages total number of pages
     * @param currentPage current page number (1-based)
     * @param hasNext true if there are more pages after this one
     * @param hasPrevious true if there are pages before this one
     */
    @Schema(description = "Pagination metadata")
    public record PaginationInfo(
        @Schema(description = "Starting position (0-based)", example = "0")
        int offset,
        
        @Schema(description = "Maximum items per page", example = "100")
        int limit,
        
        @Schema(description = "Total number of items in the full range", example = "3999")
        int totalItems,
        
        @Schema(description = "Total number of pages", example = "40")
        int totalPages,
        
        @Schema(description = "Current page number (1-based)", example = "1")
        int currentPage,
        
        @Schema(description = "True if there are more pages after this one")
        boolean hasNext,
        
        @Schema(description = "True if there are pages before this one")
        boolean hasPrevious
    ) {
        /**
         * Factory method to create PaginationInfo from range parameters.
         * 
         * @param offset starting position
         * @param limit items per page
         * @param totalItems total items in range
         * @return PaginationInfo with computed values
         */
        public static PaginationInfo of(int offset, int limit, int totalItems) {
            int totalPages = (int) Math.ceil((double) totalItems / limit);
            int currentPage = (offset / limit) + 1;
            boolean hasNext = offset + limit < totalItems;
            boolean hasPrevious = offset > 0;
            
            return new PaginationInfo(offset, limit, totalItems, totalPages, currentPage, hasNext, hasPrevious);
        }
    }
    
    /**
     * Factory method to create a PagedRangeResult.
     * 
     * @param conversions the list of conversion results for this page
     * @param offset starting position
     * @param limit items per page
     * @param totalItems total items in the full range
     * @return a new PagedRangeResult instance
     */
    public static PagedRangeResult of(List<ConversionResult> conversions, int offset, int limit, int totalItems) {
        return new PagedRangeResult(
            List.copyOf(conversions),
            PaginationInfo.of(offset, limit, totalItems)
        );
    }
    
    /**
     * Returns the number of conversions in this page.
     * 
     * @return the count of conversions in current page
     */
    public int size() {
        return conversions != null ? conversions.size() : 0;
    }
}

