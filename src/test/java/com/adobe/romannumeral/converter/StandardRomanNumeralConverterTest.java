package com.adobe.romannumeral.converter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StandardRomanNumeralConverter.
 * 
 * Tests cover:
 * - Basic conversions (1-10)
 * - Subtractive notation (4, 9, 40, 90, 400, 900)
 * - Boundary values (1, 3999)
 * - Edge cases and error handling
 * - Cache initialization
 */
@DisplayName("StandardRomanNumeralConverter Tests")
class StandardRomanNumeralConverterTest {

    private StandardRomanNumeralConverter converter;

    @BeforeEach
    void setUp() {
        converter = new StandardRomanNumeralConverter();
        converter.initialize(); // Manually initialize cache for testing
    }

    @Nested
    @DisplayName("Basic Conversions (1-10)")
    class BasicConversions {

        @ParameterizedTest(name = "{0} should convert to {1}")
        @CsvSource({
            "1, I",
            "2, II",
            "3, III",
            "4, IV",
            "5, V",
            "6, VI",
            "7, VII",
            "8, VIII",
            "9, IX",
            "10, X"
        })
        void shouldConvertBasicNumbers(int input, String expected) {
            assertEquals(expected, converter.convert(input));
        }
    }

    @Nested
    @DisplayName("Subtractive Notation")
    class SubtractiveNotation {

        @ParameterizedTest(name = "{0} should convert to {1}")
        @CsvSource({
            "4, IV",
            "9, IX",
            "40, XL",
            "90, XC",
            "400, CD",
            "900, CM"
        })
        void shouldHandleSubtractiveNotation(int input, String expected) {
            assertEquals(expected, converter.convert(input));
        }
    }

    @Nested
    @DisplayName("Complex Numbers")
    class ComplexNumbers {

        @ParameterizedTest(name = "{0} should convert to {1}")
        @CsvSource({
            "42, XLII",
            "99, XCIX",
            "255, CCLV",
            "499, CDXCIX",
            "1994, MCMXCIV",
            "2023, MMXXIII",
            "3888, MMMDCCCLXXXVIII"
        })
        void shouldConvertComplexNumbers(int input, String expected) {
            assertEquals(expected, converter.convert(input));
        }
    }

    @Nested
    @DisplayName("Boundary Values")
    class BoundaryValues {

        @Test
        @DisplayName("Should convert minimum value (1)")
        void shouldConvertMinimumValue() {
            assertEquals("I", converter.convert(1));
        }

        @Test
        @DisplayName("Should convert maximum value (3999)")
        void shouldConvertMaximumValue() {
            assertEquals("MMMCMXCIX", converter.convert(3999));
        }

        @Test
        @DisplayName("Should convert 255 (original max requirement)")
        void shouldConvert255() {
            assertEquals("CCLV", converter.convert(255));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @ParameterizedTest(name = "Should throw exception for {0}")
        @ValueSource(ints = {0, -1, -100, 4000, 5000, Integer.MAX_VALUE, Integer.MIN_VALUE})
        void shouldThrowExceptionForInvalidInput(int invalidInput) {
            assertThrows(IllegalArgumentException.class, () -> converter.convert(invalidInput));
        }

        @Test
        @DisplayName("Exception message should include the invalid value")
        void exceptionMessageShouldIncludeValue() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, 
                () -> converter.convert(0)
            );
            assertTrue(ex.getMessage().contains("0"));
        }
    }

    @Nested
    @DisplayName("Cache Validation")
    class CacheValidation {

        @Test
        @DisplayName("Cache should contain all 3999 entries")
        void cacheShouldContainAllEntries() {
            assertEquals(3999, converter.getCacheSize());
        }

        @Test
        @DisplayName("Multiple calls should return same result (cache consistency)")
        void multipleCalls_ShouldReturnSameResult() {
            String first = converter.convert(1994);
            String second = converter.convert(1994);
            assertSame(first, second, "Should return cached instance");
        }
    }

    @Nested
    @DisplayName("Range Validation")
    class RangeValidation {

        @ParameterizedTest(name = "{0} should be valid")
        @ValueSource(ints = {1, 100, 255, 1000, 3999})
        void shouldAcceptValidRange(int validInput) {
            assertTrue(converter.isValidRange(validInput));
        }

        @ParameterizedTest(name = "{0} should be invalid")
        @ValueSource(ints = {0, -1, 4000, 10000})
        void shouldRejectInvalidRange(int invalidInput) {
            assertFalse(converter.isValidRange(invalidInput));
        }
    }
}

