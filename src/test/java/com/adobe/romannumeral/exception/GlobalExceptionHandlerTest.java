package com.adobe.romannumeral.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 * Tests all exception handler methods directly for comprehensive coverage.
 */
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MDC.clear();
    }

    @Test
    @DisplayName("handleInvalidInputException returns 400 with message")
    void shouldHandleInvalidInputException() {
        InvalidInputException ex = new InvalidInputException("Number must be between 1 and 3999");
        
        ResponseEntity<String> response = handler.handleInvalidInputException(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertTrue(response.getBody().contains("Number must be between 1 and 3999"));
    }

    @Test
    @DisplayName("handleIllegalArgumentException returns 400 with message")
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument provided");
        
        ResponseEntity<String> response = handler.handleIllegalArgumentException(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertTrue(response.getBody().contains("Invalid argument provided"));
    }

    @Test
    @DisplayName("handleMissingParameter returns 400 with parameter name")
    void shouldHandleMissingParameter() {
        MissingServletRequestParameterException ex = 
            new MissingServletRequestParameterException("query", "Integer");
        
        ResponseEntity<String> response = handler.handleMissingParameter(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertTrue(response.getBody().contains("query"));
    }

    @Test
    @DisplayName("handleTypeMismatch returns 400 with parameter details")
    void shouldHandleTypeMismatch() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
            "abc", Integer.class, "query", null, new NumberFormatException("For input string: \"abc\"")
        );
        
        ResponseEntity<String> response = handler.handleTypeMismatch(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertTrue(response.getBody().contains("abc"));
        assertTrue(response.getBody().contains("query"));
    }

    @Test
    @DisplayName("handleNumberFormatException returns 400")
    void shouldHandleNumberFormatException() {
        NumberFormatException ex = new NumberFormatException("For input string: \"xyz\"");
        
        ResponseEntity<String> response = handler.handleNumberFormatException(ex);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertTrue(response.getBody().contains("Invalid number format"));
    }

    @Test
    @DisplayName("handleNoResourceFound returns 404 with path")
    void shouldHandleNoResourceFound() throws NoResourceFoundException {
        NoResourceFoundException ex = new NoResourceFoundException(
            org.springframework.http.HttpMethod.GET, "/nonexistent"
        );
        
        ResponseEntity<String> response = handler.handleNoResourceFound(ex);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertTrue(response.getBody().contains("not found"));
    }

    @Test
    @DisplayName("handleGenericException returns 500 without correlation ID")
    void shouldHandleGenericExceptionWithoutCorrelationId() {
        Exception ex = new RuntimeException("Unexpected error");
        MDC.remove("correlationId");
        
        ResponseEntity<String> response = handler.handleGenericException(ex);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertTrue(response.getBody().contains("unexpected error"));
        assertFalse(response.getBody().contains("Reference:"));
    }

    @Test
    @DisplayName("handleGenericException returns 500 with correlation ID")
    void shouldHandleGenericExceptionWithCorrelationId() {
        Exception ex = new RuntimeException("Unexpected error");
        MDC.put("correlationId", "test-123");
        
        ResponseEntity<String> response = handler.handleGenericException(ex);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertTrue(response.getBody().contains("unexpected error"));
        assertTrue(response.getBody().contains("Reference: test-123"));
        
        MDC.remove("correlationId");
    }

    @Test
    @DisplayName("All error responses start with 'Error:'")
    void allErrorResponsesShouldStartWithErrorPrefix() {
        InvalidInputException ex1 = new InvalidInputException("test");
        IllegalArgumentException ex2 = new IllegalArgumentException("test");
        NumberFormatException ex3 = new NumberFormatException("test");
        RuntimeException ex4 = new RuntimeException("test");
        
        assertTrue(handler.handleInvalidInputException(ex1).getBody().startsWith("Error:"));
        assertTrue(handler.handleIllegalArgumentException(ex2).getBody().startsWith("Error:"));
        assertTrue(handler.handleNumberFormatException(ex3).getBody().startsWith("Error:"));
        assertTrue(handler.handleGenericException(ex4).getBody().startsWith("Error:"));
    }
}

