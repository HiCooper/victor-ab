package com.gateflow.victor.pipeline.consumer;

import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
    public void consumeBatch(List<ConsumerRecord<String, EventDTO>> records) {
        log.debug("Received {} events from Kafka", records.size());
        
        List<EventDTO> events = new ArrayList<>();
        for (ConsumerRecord<String, EventDTO> record : records) {
            events.add(record.value());
        }
        
        writer.writeBatch(events);
    }
}