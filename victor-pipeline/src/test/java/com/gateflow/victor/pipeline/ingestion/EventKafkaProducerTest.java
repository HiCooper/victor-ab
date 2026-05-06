package com.gateflow.victor.pipeline.ingestion;

import com.gateflow.victor.pipeline.config.PipelineProperties;
import com.gateflow.victor.pipeline.ingestion.dto.EventDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventKafkaProducerTest {

    @Mock
    private KafkaTemplate<String, EventDTO> kafkaTemplate;

    @Mock
    private PipelineProperties properties;

    @InjectMocks
    private EventKafkaProducer producer;

    @Test
    void shouldSendEventToKafka() {
        when(properties.getKafkaTopic()).thenReturn("victor-events");
        when(kafkaTemplate.send(anyString(), anyString(), any(EventDTO.class)))
            .thenReturn(CompletableFuture.completedFuture(null));

        EventDTO event = EventDTO.builder()
            .eventId("evt_001")
            .userId("user_001")
            .build();

        producer.sendEvent(event);

        verify(kafkaTemplate).send("victor-events", "user_001", event);
    }

    @Test
    void shouldHandleSendFailure() {
        when(properties.getKafkaTopic()).thenReturn("victor-events");
        when(kafkaTemplate.send(anyString(), anyString(), any(EventDTO.class)))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka error")));

        EventDTO event = EventDTO.builder()
            .eventId("evt_002")
            .userId("user_002")
            .build();

        producer.sendEvent(event); // should not throw, just log

        verify(kafkaTemplate).send("victor-events", "user_002", event);
    }
}