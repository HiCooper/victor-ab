package com.gateflow.victor.pipeline.ingestion;

import com.gateflow.victor.pipeline.config.PipelineProperties;
import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventKafkaProducer {

    private final KafkaTemplate<String, EventDTO> kafkaTemplate;
    private final PipelineProperties properties;

    public void sendEvent(EventDTO event) {
        String topic = properties.getKafkaTopic();
        String key = event.getUserId();

        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send event to Kafka: {}", event.getEventId(), ex);
                    } else {
                        log.info("Event sent to Kafka: {}", event.getEventId());
                    }
                });
    }
}