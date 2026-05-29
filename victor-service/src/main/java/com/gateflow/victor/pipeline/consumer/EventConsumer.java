package com.gateflow.victor.pipeline.consumer;

import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka event consumer with batch processing.
 *
 * Error handling: Spring Boot auto-configures DefaultErrorHandler with 10 retries
 * (FixedBackOff(0, 1s)). After exhausting retries, failed records are logged and
 * the offset is committed (messages are silently dropped).
 *
 * TODO: Add DeadLetterPublishingRecoverer to route permanently-failed messages
 *       to a dead-letter topic (e.g., victor-events-dlt) for inspection and replay.
 *       Requires configuring a KafkaTemplate with:
 *         - DeadLetterPublishingRecoverer(kafkaTemplate)
 *         - DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 10L))
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final ClickHouseWriter writer;

    @KafkaListener(
        topics = "${victor.pipeline.kafka-topic:victor-events}",
        groupId = "victor-consumer",
        batch = "true"
    )
    public void consumeBatch(List<ConsumerRecord<String, EventDTO>> records, Acknowledgment acknowledgment) {
        log.debug("Received {} events from Kafka", records.size());

        List<EventDTO> events = new ArrayList<>();
        for (ConsumerRecord<String, EventDTO> record : records) {
            events.add(record.value());
        }

        try {
            writer.writeBatch(events);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process batch of {} events, re-throwing for Kafka retry", events.size(), e);
            throw new org.springframework.kafka.KafkaException("Batch processing failed", e);
        }
    }
}