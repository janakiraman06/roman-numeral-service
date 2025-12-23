package com.adobe.romannumeral.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Roman Numeral API.
 * 
 * <p>These tests verify the complete request/response cycle including:</p>
 * <ul>
 *   <li>HTTP endpoint accessibility</li>
 *   <li>JSON response format for success cases</li>
 *   <li>Plain text response format for error cases</li>
 *   <li>Correct HTTP status codes</li>
 *   <li>Content-Type headers</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Roman Numeral API Integration Tests")
class RomanNumeralIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Single Conversion Endpoint")
    class SingleConversionTests {

        @Test
        @DisplayName("GET /romannumeral?query=1 returns I")
        void shouldConvertOneToI() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.input").value("1"))
                .andExpect(jsonPath("$.output").value("I"));
        }

        @Test
        @DisplayName("GET /romannumeral?query=42 returns XLII")
        void shouldConvert42ToXLII() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "42"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.input").value("42"))
                .andExpect(jsonPath("$.output").value("XLII"));
        }

        @Test
        @DisplayName("GET /romannumeral?query=1994 returns MCMXCIV")
        void shouldConvert1994ToMCMXCIV() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "1994"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.input").value("1994"))
                .andExpect(jsonPath("$.output").value("MCMXCIV"));
        }

        @Test
        @DisplayName("GET /romannumeral?query=3999 returns MMMCMXCIX")
        void shouldConvert3999ToMMMCMXCIX() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "3999"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.input").value("3999"))
                .andExpect(jsonPath("$.output").value("MMMCMXCIX"));
        }

        @Test
        @DisplayName("GET /romannumeral?query=255 returns CCLV (original max requirement)")
        void shouldConvert255ToCCLV() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "255"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.input").value("255"))
                .andExpect(jsonPath("$.output").value("CCLV"));
        }
    }

    @Nested
    @DisplayName("Range Conversion Endpoint")
    class RangeConversionTests {

        @Test
        @DisplayName("GET /romannumeral?min=1&max=3 returns conversions array")
        void shouldConvertRange1To3() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("min", "1")
                    .param("max", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.conversions").isArray())
                .andExpect(jsonPath("$.conversions", hasSize(3)))
                .andExpect(jsonPath("$.conversions[0].input").value("1"))
                .andExpect(jsonPath("$.conversions[0].output").value("I"))
                .andExpect(jsonPath("$.conversions[1].input").value("2"))
                .andExpect(jsonPath("$.conversions[1].output").value("II"))
                .andExpect(jsonPath("$.conversions[2].input").value("3"))
                .andExpect(jsonPath("$.conversions[2].output").value("III"));
        }

        @Test
        @DisplayName("GET /romannumeral?min=1&max=10 returns 10 conversions in order")
        void shouldConvertRange1To10InOrder() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("min", "1")
                    .param("max", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions", hasSize(10)))
                .andExpect(jsonPath("$.conversions[0].input").value("1"))
                .andExpect(jsonPath("$.conversions[9].input").value("10"))
                .andExpect(jsonPath("$.conversions[9].output").value("X"));
        }

        @Test
        @DisplayName("Range conversion results are in ascending order")
        void shouldReturnResultsInAscendingOrder() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("min", "5")
                    .param("max", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversions[0].input").value("5"))
                .andExpect(jsonPath("$.conversions[1].input").value("6"))
                .andExpect(jsonPath("$.conversions[2].input").value("7"))
                .andExpect(jsonPath("$.conversions[3].input").value("8"));
        }
    }

    @Nested
    @DisplayName("Error Handling - Plain Text Responses")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Invalid integer returns 400 with plain text")
        void shouldReturn400ForInvalidInteger() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Invalid value")));
        }

        @Test
        @DisplayName("Out of range (0) returns 400 with plain text")
        void shouldReturn400ForZero() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("between 1 and 3999")));
        }

        @Test
        @DisplayName("Out of range (4000) returns 400 with plain text")
        void shouldReturn400For4000() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "4000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("between 1 and 3999")));
        }

        @Test
        @DisplayName("min >= max returns 400 with plain text")
        void shouldReturn400WhenMinGreaterThanMax() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("min", "10")
                    .param("max", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("must be less than")));
        }

        @Test
        @DisplayName("min == max returns 400 with plain text")
        void shouldReturn400WhenMinEqualsMax() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("min", "5")
                    .param("max", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("must be less than")));
        }

        @Test
        @DisplayName("Missing all parameters returns 400 with plain text")
        void shouldReturn400WhenNoParameters() throws Exception {
            mockMvc.perform(get("/romannumeral"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Missing required parameter")));
        }

        @Test
        @DisplayName("Only min provided returns 400 with plain text")
        void shouldReturn400WhenOnlyMinProvided() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("min", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Both")));
        }

        @Test
        @DisplayName("Only max provided returns 400 with plain text")
        void shouldReturn400WhenOnlyMaxProvided() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("max", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("Both")));
        }

        @Test
        @DisplayName("Negative number returns 400 with plain text")
        void shouldReturn400ForNegativeNumber() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN));
        }
    }

    @Nested
    @DisplayName("Content-Type Verification")
    class ContentTypeTests {

        @Test
        @DisplayName("Success response has application/json content type")
        void successResponseShouldBeJson() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("Error response has text/plain content type")
        void errorResponseShouldBePlainText() throws Exception {
            mockMvc.perform(get("/romannumeral")
                    .param("query", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN));
        }
    }
}

