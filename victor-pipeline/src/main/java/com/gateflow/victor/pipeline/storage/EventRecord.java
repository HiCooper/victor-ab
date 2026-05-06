package com.gateflow.victor.pipeline.storage;

import lombok.Data;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class EventRecord {
    private LocalDateTime eventDate;
    private String eventId;
    private String eventType;
    private String userId;
    private LocalDateTime timestamp;
    private String platform;
    private String deviceId;
    private String sessionId;
    private List<String> expIds;
    private List<String> variants;
    private List<String> layers;
    private String properties;
    private LocalDateTime receivedAt;
}