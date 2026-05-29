package com.gateflow.victor.sdk;

import lombok.Builder;
import lombok.Data;

/**
 * Victor SDK 配置类
 */
@Data
@Builder
public class VictorConfig {

    /**
     * 服务端URL
     */
    @Builder.Default
    private String serverUrl = "http://localhost:8080";

    /**
     * API Key (可选，用于鉴权)
     */
    private String apiKey;

    /**
     * 平台标识 (web/android/ios/server)
     */
    @Builder.Default
    private String platform = "server";

    /**
     * 配置轮询间隔 (秒)
     */
    @Builder.Default
    private int pollingInterval = 30;

    /**
     * 配置缓存有效期 (秒)
     */
    @Builder.Default
    private long cacheExpiry = 7 * 24 * 3600; // 7天

    /**
     * 本地缓存最大容量
     */
    @Builder.Default
    private int cacheMaxSize = 1000;

    /**
     * HTTP请求超时 (毫秒)
     */
    @Builder.Default
    private int requestTimeout = 5000;

    /**
     * 重试次数
     */
    @Builder.Default
    private int retryCount = 3;

    /**
     * 是否启用事件上报（false 时不入队，零开销）
     */
    @Builder.Default
    private boolean eventTrackingEnabled = true;

    /**
     * 事件批量上报阈值（达到此数量时立即刷新）
     */
    @Builder.Default
    private int eventBatchSize = 100;

    /**
     * 事件刷新间隔（秒），定时将队列中事件批量上报
     */
    @Builder.Default
    private int eventFlushIntervalSeconds = 5;

    /**
     * 事件队列最大容量（超出时丢弃最旧事件）
     */
    @Builder.Default
    private int eventQueueCapacity = 10000;

    /**
     * 离线缓存目录 (默认: ~/.victor)
     */
    private String cacheDir;
}