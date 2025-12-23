package com.adobe.romannumeral.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP filter that adds correlation IDs to requests for distributed tracing.
 * 
 * <p>This filter generates or propagates a correlation ID for each request,
 * making it available in logs via MDC (Mapped Diagnostic Context) and
 * returning it in the response header.</p>
 * 
 * <h2>Correlation ID Flow:</h2>
 * <ol>
 *   <li>Check for incoming X-Correlation-ID header</li>
 *   <li>If not present, generate a new UUID</li>
 *   <li>Add to MDC for logging</li>
 *   <li>Add to response header</li>
 * </ol>
 * 
 * <h2>Usage in Logs:</h2>
 * <pre>
 * 2024-01-15 10:30:00 [main] [abc12345] INFO Controller - Processing request
 * </pre>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@Component
@Order(0) // Execute first in filter chain
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String correlationId = getOrGenerateCorrelationId(request);
        
        try {
            // Add to MDC for logging
            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            
            // Add to response header
            response.addHeader(CORRELATION_ID_HEADER, correlationId);
            
            filterChain.doFilter(request, response);
        } finally {
            // Clean up MDC to prevent memory leaks
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    /**
     * Gets the correlation ID from the request header or generates a new one.
     * 
     * @param request the HTTP request
     * @return the correlation ID (existing or newly generated)
     */
    private String getOrGenerateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        
        if (correlationId == null || correlationId.isBlank()) {
            // Generate a short UUID for readability
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }
        
        return correlationId;
    }
}

