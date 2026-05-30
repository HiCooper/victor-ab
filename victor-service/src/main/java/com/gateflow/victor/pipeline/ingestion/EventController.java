package com.gateflow.victor.pipeline.ingestion;

import com.gateflow.victor.pipeline.ingestion.dto.EventRequest;
import com.gateflow.victor.pipeline.ingestion.dto.EventResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventKafkaProducer kafkaProducer;

    @PostMapping
    public ResponseEntity<EventResponse> collectEvents(@Valid @RequestBody EventRequest request) {
        
        List<String> errors = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;

        for (var event : request.getEvents()) {
            try {
                if (event.getTimestamp() == null || event.getTimestamp() <= 0) {
                    errors.add("Event " + event.getEventId() + ": invalid timestamp");
                    rejected++;
                    continue;
                }
                
                kafkaProducer.sendEvent(event);
                accepted++;
            } catch (Exception e) {
                log.error("Failed to send event: {}", event.getEventId(), e);
                errors.add("Event " + event.getEventId() + ": " + e.getMessage());
                rejected++;
            }
        }

        return ResponseEntity.ok(EventResponse.builder()
            .accepted(accepted)
            .rejected(rejected)
            .errors(errors)
            .build());
    }
}