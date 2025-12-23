package com.adobe.romannumeral.exception;

/**
 * Exception thrown when input validation fails for Roman numeral conversion.
 * 
 * <p>This exception is used to indicate invalid user input, such as:</p>
 * <ul>
 *   <li>Non-integer query parameter</li>
 *   <li>Value outside valid range (1-3999)</li>
 *   <li>Invalid range parameters (min >= max)</li>
 *   <li>Missing required parameters</li>
 * </ul>
 * 
 * <p>This exception results in a 400 Bad Request HTTP response with a
 * plain text error message as per the API specification.</p>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
public class InvalidInputException extends RuntimeException {

    /**
     * Constructs an InvalidInputException with the specified message.
     * 
     * @param message the error message describing the validation failure
     */
    public InvalidInputException(String message) {
        super(message);
    }

    /**
     * Constructs an InvalidInputException with a message and cause.
     * 
     * @param message the error message
     * @param cause   the underlying cause of the exception
     */
    public InvalidInputException(String message, Throwable cause) {
        super(message, cause);
    }
}

