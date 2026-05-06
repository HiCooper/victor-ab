package com.gateflow.victor.pipeline.ingestion;

import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import com.gateflow.victor.pipeline.ingestion.dto.EventRequest;
import com.gateflow.victor.pipeline.ingestion.dto.EventResponse;
import com.gateflow.victor.pipeline.ingestion.dto.ExperimentTagDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventKafkaProducer kafkaProducer;

    @InjectMocks
    private EventController controller;

    @Test
    void shouldAcceptValidEvents() {
        EventDTO event = EventDTO.builder()
            .eventId("evt_001")
            .eventType("click")
            .userId("user_001")
            .timestamp(System.currentTimeMillis())
            .build();

        EventRequest request = new EventRequest();
        request.setEvents(Arrays.asList(event));

        EventResponse response = controller.collectEvents(request).getBody();

        assertEquals(1, response.getAccepted());
        assertEquals(0, response.getRejected());
        assertTrue(response.getErrors().isEmpty());
        
        verify(kafkaProducer).sendEvent(event);
    }

    @Test
    void shouldRejectInvalidTimestamp() {
        EventDTO event = EventDTO.builder()
            .eventId("evt_002")
            .eventType("click")
            .userId("user_001")
            .timestamp(-1L) // invalid
            .build();

        EventRequest request = new EventRequest();
        request.setEvents(Arrays.asList(event));

        EventResponse response = controller.collectEvents(request).getBody();

        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getRejected());
        assertFalse(response.getErrors().isEmpty());
        
        verify(kafkaProducer, never()).sendEvent(any());
    }

    @Test
    void shouldHandleBatchEvents() {
        EventDTO event1 = EventDTO.builder()
            .eventId("evt_001")
            .eventType("click")
            .userId("user_001")
            .timestamp(System.currentTimeMillis())
            .build();

        EventDTO event2 = EventDTO.builder()
            .eventId("evt_002")
            .eventType("view")
            .userId("user_002")
            .timestamp(System.currentTimeMillis())
            .experimentTags(Arrays.asList(
                ExperimentTagDTO.builder().expId("exp_001").variant("A").layer("rec").build()
            ))
            .build();

        EventRequest request = new EventRequest();
        request.setEvents(Arrays.asList(event1, event2));

        EventResponse response = controller.collectEvents(request).getBody();

        assertEquals(2, response.getAccepted());
        assertEquals(0, response.getRejected());
        
        verify(kafkaProducer, times(2)).sendEvent(any());
    }

    @Test
    void shouldHandleKafkaFailure() {
        EventDTO event = EventDTO.builder()
            .eventId("evt_003")
            .eventType("click")
            .userId("user_001")
            .timestamp(System.currentTimeMillis())
            .build();

        doThrow(new RuntimeException("Kafka connection failed"))
            .when(kafkaProducer).sendEvent(event);

        EventRequest request = new EventRequest();
        request.setEvents(Arrays.asList(event));

        EventResponse response = controller.collectEvents(request).getBody();

        assertEquals(0, response.getAccepted());
        assertEquals(1, response.getRejected());
    }
}