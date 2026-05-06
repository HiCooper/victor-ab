package com.gateflow.victor.pipeline.consumer;

import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import com.gateflow.victor.pipeline.storage.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClickHouseWriterTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private ClickHouseWriter writer;

    @Test
    void shouldWriteBatchToClickHouse() {
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
            .build();

        writer.writeBatch(Arrays.asList(event1, event2));

        verify(eventRepository).insert(event1);
        verify(eventRepository).insert(event2);
    }

    @Test
    void shouldContinueOnError() {
        EventDTO goodEvent = EventDTO.builder()
            .eventId("evt_good")
            .eventType("click")
            .userId("user_001")
            .timestamp(System.currentTimeMillis())
            .build();

        EventDTO badEvent = EventDTO.builder()
            .eventId("evt_bad")
            .eventType("click")
            .userId("user_002")
            .timestamp(System.currentTimeMillis())
            .build();

        doThrow(new RuntimeException("DB error"))
            .when(eventRepository).insert(badEvent);

        writer.writeBatch(Arrays.asList(badEvent, goodEvent));

        verify(eventRepository).insert(badEvent);
        verify(eventRepository).insert(goodEvent);
    }
}