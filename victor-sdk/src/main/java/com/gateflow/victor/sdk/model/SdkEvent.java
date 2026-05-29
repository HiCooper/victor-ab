package com.gateflow.victor.sdk.model;

import java.util.Map;
import java.util.UUID;

/**
 * SDK 事件模型 — 异步上报到 AB 系统
 */
public class SdkEvent {

    private String eventId;
    private String userId;
    private String event;
    private String eventType;
    private String experimentKey;
    private String variant;
    private String layerId;
    private long timestamp;
    private Map<String, Object> properties;

    public SdkEvent() {
        this.eventId = UUID.randomUUID().toString().replace("-", "");
        this.timestamp = System.currentTimeMillis();
    }

    public static SdkEvent exposure(String userId, String experimentKey, String variant, String layerId) {
        SdkEvent e = new SdkEvent();
        e.userId = userId;
        e.event = "exposure";
        e.eventType = "exposure";
        e.experimentKey = experimentKey;
        e.variant = variant;
        e.layerId = layerId;
        return e;
    }

    public static SdkEvent custom(String userId, String eventType, Map<String, Object> properties) {
        SdkEvent e = new SdkEvent();
        e.userId = userId;
        e.event = eventType;
        e.eventType = eventType;
        e.properties = properties;
        return e;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getExperimentKey() { return experimentKey; }
    public void setExperimentKey(String experimentKey) { this.experimentKey = experimentKey; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public String getLayerId() { return layerId; }
    public void setLayerId(String layerId) { this.layerId = layerId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
}
