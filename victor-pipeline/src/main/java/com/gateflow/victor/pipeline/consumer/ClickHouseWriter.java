package com.gateflow.victor.pipeline.consumer;

import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import com.gateflow.victor.pipeline.storage.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClickHouseWriter {
    
    private final EventRepository eventRepository;

    public void writeBatch(List<EventDTO> events) {
        for (EventDTO event : events) {
            try {
                eventRepository.insert(event);
            } catch (Exception e) {
                log.error("Failed to write event: {}", event.getEventId(), e);
            }
        }
        log.info("Written {} events to ClickHouse", events.size());
    }
}