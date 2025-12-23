package com.adobe.romannumeral.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler for the Roman Numeral API.
 * 
 * <p>This handler intercepts exceptions and converts them to appropriate
 * HTTP responses. As per the API specification, error responses are
 * returned in <b>plain text format</b>, not JSON.</p>
 * 
 * <h2>Specification Compliance:</h2>
 * <blockquote>
 * "Errors can be returned in plain text format, but success responses 
 * must include a JSON payload..."
 * </blockquote>
 * 
 * <h2>Production Note:</h2>
 * <p>In a production environment, we would implement RFC 7807 (Problem Details
 * for HTTP APIs) for structured JSON error responses. Plain text is used here
 * as permitted by the specification for simplicity.</p>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles custom InvalidInputException for validation errors.
     * 
     * @param ex the InvalidInputException
     * @return ResponseEntity with plain text error message and 400 status
     */
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<String> handleInvalidInputException(InvalidInputException ex) {
        logger.warn("Invalid input: {}", ex.getMessage());
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.TEXT_PLAIN)
            .body("Error: " + ex.getMessage());
    }

    /**
     * Handles IllegalArgumentException from service/converter layers.
     * 
     * @param ex the IllegalArgumentException
     * @return ResponseEntity with plain text error message and 400 status
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.warn("Illegal argument: {}", ex.getMessage());
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.TEXT_PLAIN)
            .body("Error: " + ex.getMessage());
    }

    /**
     * Handles missing required request parameters.
     * 
     * <p>Triggered when a required query parameter is not provided.</p>
     * 
     * @param ex the MissingServletRequestParameterException
     * @return ResponseEntity with plain text error message and 400 status
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<String> handleMissingParameter(MissingServletRequestParameterException ex) {
        String paramName = ex.getParameterName();
        logger.warn("Missing required parameter: {}", paramName);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.TEXT_PLAIN)
            .body("Error: Missing required parameter '" + paramName + "'");
    }

    /**
     * Handles type mismatch exceptions (e.g., non-integer for query parameter).
     * 
     * <p>Triggered when a parameter cannot be converted to the expected type,
     * such as passing "abc" for an integer parameter.</p>
     * 
     * @param ex the MethodArgumentTypeMismatchException
     * @return ResponseEntity with plain text error message and 400 status
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String paramName = ex.getName();
        Object value = ex.getValue();
        logger.warn("Type mismatch for parameter '{}': {}", paramName, value);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.TEXT_PLAIN)
            .body("Error: Invalid value '" + value + "' for parameter '" + paramName 
                + "'. Please provide a valid integer.");
    }

    /**
     * Handles NumberFormatException for parsing errors.
     * 
     * @param ex the NumberFormatException
     * @return ResponseEntity with plain text error message and 400 status
     */
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<String> handleNumberFormatException(NumberFormatException ex) {
        logger.warn("Number format error: {}", ex.getMessage());
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.TEXT_PLAIN)
            .body("Error: Invalid number format. Please provide a valid integer.");
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * 
     * <p>This ensures that even unexpected errors return a proper response
     * rather than exposing stack traces or internal details.</p>
     * 
     * @param ex the Exception
     * @return ResponseEntity with generic error message and 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred", ex);
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.TEXT_PLAIN)
            .body("Error: An unexpected error occurred. Please try again later.");
    }
}

