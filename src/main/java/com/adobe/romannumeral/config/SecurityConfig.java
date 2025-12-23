package com.adobe.romannumeral.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

/**
 * Security configuration for the Roman Numeral API.
 * 
 * <p>This configuration sets up security headers and access rules for the API.
 * The API is publicly accessible (no authentication required) as per the
 * assessment requirements, but includes security best practices:</p>
 * 
 * <h2>Security Headers:</h2>
 * <ul>
 *   <li>X-Content-Type-Options: nosniff</li>
 *   <li>X-Frame-Options: DENY</li>
 *   <li>X-XSS-Protection: 1; mode=block</li>
 *   <li>Content-Security-Policy: default-src 'self'</li>
 *   <li>Referrer-Policy: strict-origin-when-cross-origin</li>
 * </ul>
 * 
 * <h2>Design Decision:</h2>
 * <p>No authentication is implemented as the API specification does not require it.
 * Rate limiting is used instead to prevent abuse. In production with sensitive data,
 * we would implement OAuth2, JWT, or API key authentication.</p>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Configures the security filter chain for the application.
     * 
     * @param http the HttpSecurity to configure
     * @return the configured SecurityFilterChain
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless API
            .csrf(AbstractHttpConfigurer::disable)
            
            // Stateless session management
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure authorization
            .authorizeHttpRequests(auth -> auth
                // Public API endpoints
                .requestMatchers("/romannumeral/**").permitAll()
                
                // Swagger/OpenAPI endpoints
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                
                // Actuator endpoints (on separate port, but also allow here)
                .requestMatchers("/actuator/**").permitAll()
                
                // All other requests require authentication (none expected)
                .anyRequest().authenticated()
            )
            
            // Configure security headers
            .headers(headers -> headers
                // Prevent MIME type sniffing
                .contentTypeOptions(contentType -> {})
                
                // Prevent clickjacking
                .frameOptions(frame -> frame.deny())
                
                // XSS protection
                .xssProtection(xss -> 
                    xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                
                // Content Security Policy
                .contentSecurityPolicy(csp -> 
                    csp.policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'"))
                
                // Referrer Policy
                .referrerPolicy(referrer -> 
                    referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                
                // Permissions Policy (formerly Feature-Policy)
                .permissionsPolicy(permissions -> 
                    permissions.policy("geolocation=(), microphone=(), camera=()"))
            );
        
        return http.build();
    }
}

