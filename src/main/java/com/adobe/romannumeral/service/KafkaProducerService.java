package com.adobe.romannumeral.service;

import com.adobe.romannumeral.event.ConversionEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing conversion events to Kafka.
 *
 * <p>This service implements the producer side of the event-driven architecture,
 * publishing conversion events to the Bronze layer topic for downstream processing.</p>
 *
 * <p>Design considerations:
 * <ul>
 *   <li>Async publishing to avoid blocking HTTP requests</li>
 *   <li>Fire-and-forget with error logging (non-critical path)</li>
 *   <li>JSON serialization for schema flexibility</li>
 *   <li>Metrics for monitoring publish success/failure</li>
 * </ul>
 * </p>
 *
 * <p>See ADR-011: Event-Driven Architecture for design rationale.</p>
 */
@Service
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topicName;
    private final boolean enabled;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;

    public KafkaProducerService(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${app.kafka.topic:romannumeral-events}") String topicName,
            @Value("${app.kafka.enabled:true}") boolean enabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topicName = topicName;
        this.enabled = enabled;
        
        this.publishSuccessCounter = Counter.builder("kafka.events.published")
                .tag("status", "success")
                .description("Number of events successfully published to Kafka")
                .register(meterRegistry);
        
        this.publishFailureCounter = Counter.builder("kafka.events.published")
                .tag("status", "failure")
                .description("Number of events that failed to publish to Kafka")
                .register(meterRegistry);
        
        log.info("KafkaProducerService initialized. Topic: {}, Enabled: {}", topicName, enabled);
    }

    /**
     * Publishes a conversion event to Kafka asynchronously.
     *
     * <p>This method is non-blocking and returns immediately. Publishing failures
     * are logged but do not affect the main request flow.</p>
     *
     * @param event the conversion event to publish
     */
    public void publishEvent(ConversionEvent event) {
        if (!enabled) {
            log.debug("Kafka publishing disabled, skipping event: {}", event.eventId());
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
            String key = event.eventType() + "-" + (event.inputNumber() != null 
                    ? event.inputNumber().toString() 
                    : event.rangeMin() + "-" + event.rangeMax());

            CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(topicName, key, payload);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event {} to Kafka: {}", 
                            event.eventId(), ex.getMessage());
                    publishFailureCounter.increment();
                } else {
                    log.debug("Published event {} to topic {} partition {} offset {}", 
                            event.eventId(),
                            result.getRecordMetadata().topic(),
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    publishSuccessCounter.increment();
                }
            });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event {}: {}", event.eventId(), e.getMessage());
            publishFailureCounter.increment();
        }
    }

    /**
     * Creates and publishes a single conversion event.
     */
    public void publishSingleConversion(
            Integer input,
            String output,
            long responseTimeNanos,
            String correlationId,
            String clientIp,
            Long userId,
            String apiKeyPrefix) {
        ConversionEvent event = ConversionEvent.single(
                input, output, responseTimeNanos, correlationId, clientIp, userId, apiKeyPrefix);
        publishEvent(event);
    }

    /**
     * Creates and publishes a range conversion event.
     */
    public void publishRangeConversion(
            Integer min,
            Integer max,
            int resultCount,
            Integer offset,
            Integer limit,
            long responseTimeNanos,
            String correlationId,
            String clientIp,
            Long userId,
            String apiKeyPrefix) {
        ConversionEvent event = ConversionEvent.range(
                min, max, resultCount, offset, limit, responseTimeNanos,
                correlationId, clientIp, userId, apiKeyPrefix);
        publishEvent(event);
    }

    /**
     * Returns whether Kafka publishing is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the topic name.
     */
    public String getTopicName() {
        return topicName;
    }
}

