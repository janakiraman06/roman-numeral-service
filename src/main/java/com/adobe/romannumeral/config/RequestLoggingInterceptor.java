package com.adobe.romannumeral.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * HTTP Request Logging Interceptor.
 * 
 * Logs every HTTP request with:
 * - HTTP method and URI
 * - Response status code
 * - Request duration in milliseconds
 * - Correlation ID (from MDC)
 * 
 * This enables request-level visibility in log aggregation systems (Loki/ELK).
 */
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("http.request");
    private static final String START_TIME_ATTR = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                             Object handler) {
        request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTR);
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
        
        String correlationId = MDC.get("correlationId");
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        int status = response.getStatus();
        
        String fullPath = query != null ? uri + "?" + query : uri;
        
        // Skip health check endpoints to reduce noise
        if (uri.contains("/actuator/health") || uri.contains("/actuator/prometheus")) {
            return;
        }
        
        if (status >= 400) {
            log.warn("method={} path={} status={} duration={}ms correlationId={}", 
                     method, fullPath, status, duration, correlationId);
        } else {
            log.info("method={} path={} status={} duration={}ms correlationId={}", 
                     method, fullPath, status, duration, correlationId);
        }
    }
}

