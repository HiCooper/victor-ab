package com.gateflow.victor.pipeline.ingestion.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventDTOTest {

    @Test
    void shouldBuildEventDTO() {
        EventDTO event = EventDTO.builder()
            .eventId("evt_20260505_abc123")
            .eventType("click")
            .userId("user_12345")
            .timestamp(1714900000000L)
            .platform("web")
            .deviceId("device_xyz")
            .sessionId("session_001")
            .build();

        assertEquals("evt_20260505_abc123", event.getEventId());
        assertEquals("click", event.getEventType());
        assertEquals("user_12345", event.getUserId());
        assertEquals(1714900000000L, event.getTimestamp());
        assertEquals("web", event.getPlatform());
    }

    @Test
    void shouldHaveNoArgsConstructor() {
        EventDTO event = new EventDTO();
        assertNotNull(event);
    }
}