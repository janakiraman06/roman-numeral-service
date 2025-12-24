package com.adobe.romannumeral.filter;

import com.adobe.romannumeral.config.RateLimitConfig;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitFilter}.
 * Tests rate limiting behavior including allowed and blocked requests.
 */
@DisplayName("RateLimitFilter Unit Tests")
class RateLimitFilterTest {

    private RateLimitFilter filter;
    private RateLimitConfig rateLimitConfig;
    private FilterChain filterChain;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig();
        // Set the rate limit value via reflection since @Value won't work in unit tests
        ReflectionTestUtils.setField(rateLimitConfig, "requestsPerMinute", 100);
        filter = new RateLimitFilter(rateLimitConfig);
        filterChain = mock(FilterChain.class);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Request under rate limit is allowed")
    void shouldAllowRequestUnderRateLimit() throws Exception {
        request.setRequestURI("/romannumeral");
        request.setRemoteAddr("127.0.0.1");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        assertNotNull(response.getHeader("X-Rate-Limit-Remaining"));
    }

    @Test
    @DisplayName("Non-API request is not rate limited")
    void shouldNotRateLimitNonApiRequests() throws Exception {
        request.setRequestURI("/actuator/health");
        request.setRemoteAddr("127.0.0.1");
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        // No rate limit headers for non-API requests
        assertNull(response.getHeader("X-Rate-Limit-Remaining"));
    }

    @Test
    @DisplayName("Request exceeding rate limit returns 429")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        String testIp = "192.168.1.100";
        
        // Make 101 requests - the 101st should be rate limited
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            MockHttpServletResponse res = new MockHttpServletResponse();
            req.setRequestURI("/romannumeral");
            req.setRemoteAddr(testIp);
            filter.doFilterInternal(req, res, filterChain);
        }
        
        // The 101st request should be blocked
        MockHttpServletRequest finalRequest = new MockHttpServletRequest();
        MockHttpServletResponse finalResponse = new MockHttpServletResponse();
        finalRequest.setRequestURI("/romannumeral");
        finalRequest.setRemoteAddr(testIp);
        
        filter.doFilterInternal(finalRequest, finalResponse, filterChain);
        
        assertEquals(429, finalResponse.getStatus()); // 429 Too Many Requests
        assertNotNull(finalResponse.getHeader("Retry-After"));
    }

    @Test
    @DisplayName("Rate limit headers are set correctly")
    void shouldSetRateLimitHeaders() throws Exception {
        request.setRequestURI("/romannumeral");
        request.setRemoteAddr("10.0.0.1");
        
        filter.doFilterInternal(request, response, filterChain);
        
        assertNotNull(response.getHeader("X-Rate-Limit-Remaining"));
        int remaining = Integer.parseInt(response.getHeader("X-Rate-Limit-Remaining"));
        assertTrue(remaining >= 0 && remaining <= 100);
    }

    @Test
    @DisplayName("Different IPs have separate rate limits")
    void shouldHaveSeparateRateLimitsPerIp() throws Exception {
        // First IP
        request.setRequestURI("/romannumeral");
        request.setRemoteAddr("1.1.1.1");
        filter.doFilterInternal(request, response, filterChain);
        
        // Second IP - should have its own bucket
        MockHttpServletRequest request2 = new MockHttpServletRequest();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        request2.setRequestURI("/romannumeral");
        request2.setRemoteAddr("2.2.2.2");
        filter.doFilterInternal(request2, response2, filterChain);
        
        // Both should be allowed
        verify(filterChain, times(2)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Swagger endpoints are not rate limited")
    void shouldNotRateLimitSwaggerEndpoints() throws Exception {
        request.setRequestURI("/swagger-ui/index.html");
        request.setRemoteAddr("127.0.0.1");
        
        filter.doFilterInternal(request, response, filterChain);
        
        verify(filterChain).doFilter(request, response);
        assertNull(response.getHeader("X-Rate-Limit-Remaining"));
    }
}

