package com.adobe.romannumeral.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.adobe.romannumeral.event.ConversionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaProducerService Tests")
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        meterRegistry = new SimpleMeterRegistry();
    }

    @Nested
    @DisplayName("When Kafka Disabled")
    class KafkaDisabled {

        @Test
        @DisplayName("should not publish events when disabled")
        void shouldNotPublishWhenDisabled() {
            KafkaProducerService service = new KafkaProducerService(
                    kafkaTemplate, objectMapper, meterRegistry, "test-topic", false);

            ConversionEvent event = ConversionEvent.single(42, "XLII", 1000L, null, null, null, null);
            service.publishEvent(event);

            verifyNoInteractions(kafkaTemplate);
            assertFalse(service.isEnabled());
        }

        @Test
        @DisplayName("should return correct topic name")
        void shouldReturnTopicName() {
            KafkaProducerService service = new KafkaProducerService(
                    kafkaTemplate, objectMapper, meterRegistry, "my-topic", false);

            assertEquals("my-topic", service.getTopicName());
        }
    }

    @Nested
    @DisplayName("When Kafka Enabled")
    class KafkaEnabled {

        private KafkaProducerService service;

        @BeforeEach
        void setUp() {
            service = new KafkaProducerService(
                    kafkaTemplate, objectMapper, meterRegistry, "test-topic", true);
        }

        @Test
        @DisplayName("should be enabled")
        void shouldBeEnabled() {
            assertTrue(service.isEnabled());
        }

        @Test
        @DisplayName("should publish event to Kafka")
        void shouldPublishEvent() {
            SendResult<String, String> sendResult = mock(SendResult.class);
            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("test-topic", 0), 0L, 0, 0L, 0, 0);
            when(sendResult.getRecordMetadata()).thenReturn(metadata);
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(sendResult));

            ConversionEvent event = ConversionEvent.single(42, "XLII", 1000L, null, null, null, null);
            service.publishEvent(event);

            verify(kafkaTemplate).send(eq("test-topic"), anyString(), anyString());
        }

        @Test
        @DisplayName("should publish single conversion")
        void shouldPublishSingleConversion() {
            SendResult<String, String> sendResult = mock(SendResult.class);
            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("test-topic", 0), 0L, 0, 0L, 0, 0);
            when(sendResult.getRecordMetadata()).thenReturn(metadata);
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(sendResult));

            service.publishSingleConversion(42, "XLII", 1000L, "corr-1", "127.0.0.1", 1L, "rns_test");

            verify(kafkaTemplate).send(eq("test-topic"), contains("SINGLE"), anyString());
        }

        @Test
        @DisplayName("should publish range conversion")
        void shouldPublishRangeConversion() {
            SendResult<String, String> sendResult = mock(SendResult.class);
            RecordMetadata metadata = new RecordMetadata(
                    new TopicPartition("test-topic", 0), 0L, 0, 0L, 0, 0);
            when(sendResult.getRecordMetadata()).thenReturn(metadata);
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(sendResult));

            service.publishRangeConversion(1, 100, 100, 0, 100, 5000L, "corr-2", "10.0.0.1", 2L, "rns_prod");

            verify(kafkaTemplate).send(eq("test-topic"), contains("RANGE"), anyString());
        }

        @Test
        @DisplayName("should handle publish failure gracefully")
        void shouldHandlePublishFailureGracefully() {
            CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
            when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

            ConversionEvent event = ConversionEvent.single(42, "XLII", 1000L, null, null, null, null);
            
            // Should not throw exception
            assertDoesNotThrow(() -> service.publishEvent(event));
        }
    }
}

