package com.gateflow.victor.sdk.model;

import java.util.Map;
import java.util.UUID;

/**
 * SDK 分流事件模型 — 异步上报分桶分配结果到 AB 系统
 */
public class SdkEvent {

    private String eventId;
    private String userId;
    private String experimentKey;
    private String bucket;
    private String layerId;
    private long timestamp;
    private Map<String, Object> properties;

    public SdkEvent() {
        this.eventId = UUID.randomUUID().toString().replace("-", "");
        this.timestamp = System.currentTimeMillis();
    }

    public static SdkEvent assignment(String userId, String experimentKey, String bucket, String layerId) {
        SdkEvent e = new SdkEvent();
        e.userId = userId;
        e.experimentKey = experimentKey;
        e.bucket = bucket;
        e.layerId = layerId;
        return e;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getExperimentKey() {
        return experimentKey;
    }

    public void setExperimentKey(String experimentKey) {
        this.experimentKey = experimentKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getLayerId() {
        return layerId;
    }

    public void setLayerId(String layerId) {
        this.layerId = layerId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
}
