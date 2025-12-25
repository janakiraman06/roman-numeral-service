package com.adobe.romannumeral.event;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConversionEvent Tests")
class ConversionEventTest {

    @Test
    @DisplayName("should create single conversion event")
    void shouldCreateSingleConversionEvent() {
        ConversionEvent event = ConversionEvent.single(
                42, "XLII", 1000000L, "corr-123", "192.168.1.1", 1L, "rns_test");

        assertNotNull(event.eventId());
        assertNotNull(event.eventTime());
        assertEquals("SINGLE", event.eventType());
        assertEquals(42, event.inputNumber());
        assertEquals("XLII", event.outputRoman());
        assertEquals(1000000L, event.responseTimeNanos());
        assertEquals("corr-123", event.correlationId());
        assertEquals("192.168.1.1", event.clientIp());
        assertEquals(1L, event.userId());
        assertEquals("rns_test", event.apiKeyPrefix());
        assertEquals("SUCCESS", event.status());
        assertNull(event.rangeMin());
        assertNull(event.rangeMax());
    }

    @Test
    @DisplayName("should create range conversion event")
    void shouldCreateRangeConversionEvent() {
        ConversionEvent event = ConversionEvent.range(
                1, 100, 100, 0, 100, 5000000L, "corr-456", "10.0.0.1", 2L, "rns_prod");

        assertNotNull(event.eventId());
        assertNotNull(event.eventTime());
        assertEquals("RANGE", event.eventType());
        assertEquals(1, event.rangeMin());
        assertEquals(100, event.rangeMax());
        assertEquals(100, event.resultCount());
        assertEquals(0, event.pageOffset());
        assertEquals(100, event.pageLimit());
        assertEquals(5000000L, event.responseTimeNanos());
        assertEquals("SUCCESS", event.status());
        assertNull(event.inputNumber());
        assertNull(event.outputRoman());
    }

    @Test
    @DisplayName("should create error event")
    void shouldCreateErrorEvent() {
        ConversionEvent event = ConversionEvent.error(
                "SINGLE", "ERROR_VALIDATION", "corr-789", "192.168.1.1");

        assertNotNull(event.eventId());
        assertEquals("SINGLE", event.eventType());
        assertEquals("ERROR_VALIDATION", event.status());
        assertEquals("corr-789", event.correlationId());
        assertNull(event.userId());
        assertNull(event.inputNumber());
    }

    @Test
    @DisplayName("should generate unique event IDs")
    void shouldGenerateUniqueEventIds() {
        ConversionEvent event1 = ConversionEvent.single(1, "I", 1000L, null, null, null, null);
        ConversionEvent event2 = ConversionEvent.single(1, "I", 1000L, null, null, null, null);

        assertNotEquals(event1.eventId(), event2.eventId());
    }
}

