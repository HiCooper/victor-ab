package com.gateflow.victor.pipeline.consumer;

import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import com.gateflow.victor.pipeline.storage.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
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

        when(eventRepository.insertBatch(anyList())).thenReturn(2);

        writer.writeBatch(Arrays.asList(event1, event2));

        verify(eventRepository).insertBatch(anyList());
    }

    @Test
    void shouldFallbackOnError() {
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

        // 批量插入失败时回退到逐条插入
        when(eventRepository.insertBatch(anyList())).thenThrow(new RuntimeException("DB error"));
        doNothing().when(eventRepository).insert(any());

        writer.writeBatch(Arrays.asList(badEvent, goodEvent));

        // 验证回退到逐条插入
        verify(eventRepository).insert(badEvent);
        verify(eventRepository).insert(goodEvent);
    }
}