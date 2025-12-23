package com.adobe.romannumeral;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Roman Numeral Conversion Service.
 * 
 * <p>This service provides REST endpoints for converting integers to Roman numerals,
 * supporting both single conversions and parallel range conversions.</p>
 * 
 * <h2>Features:</h2>
 * <ul>
 *   <li>Single integer to Roman numeral conversion (1-3999)</li>
 *   <li>Parallel range conversion using Java 21 virtual threads</li>
 *   <li>Production-ready with metrics, logging, and health checks</li>
 * </ul>
 * 
 * <h2>API Endpoints:</h2>
 * <ul>
 *   <li>GET /romannumeral?query={integer} - Single conversion</li>
 *   <li>GET /romannumeral?min={integer}&amp;max={integer} - Range conversion</li>
 * </ul>
 * 
 * @author Adobe AEM Engineering Assessment
 * @version 1.0.0
 * @see <a href="https://en.wikipedia.org/wiki/Roman_numerals">Roman Numerals - Wikipedia</a>
 */
@SpringBootApplication
public class RomanNumeralApplication {

    /**
     * Application entry point.
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(RomanNumeralApplication.class, args);
    }
}

