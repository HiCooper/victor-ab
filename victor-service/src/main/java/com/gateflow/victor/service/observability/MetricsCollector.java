package com.gateflow.victor.service.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Central observability metrics collector for GateFlow/Victor AB system.
 * Wraps Micrometer MeterRegistry to expose Prometheus-compatible metrics
 * for key operational dimensions.
 */
@Component
public class MetricsCollector {

    private final MeterRegistry registry;
    private final Counter bucketingRequests;
    private final Timer bucketingDuration;
    private final Counter configFetchTotal;
    private final Counter configHit304;
    private final Counter eventsIngested;
    private final Counter eventsIngestionErrors;
    private final Timer clickhouseQueryDuration;
    private final Counter experimentTransitions;
    private final Timer kafkaConsumerLag;

    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;

        this.bucketingRequests = Counter.builder("victor.bucketing.requests")
                .description("Total bucketing API requests")
                .register(registry);
        this.bucketingDuration = Timer.builder("victor.bucketing.duration")
                .description("Bucketing computation duration")
                .register(registry);
        this.configFetchTotal = Counter.builder("victor.config.fetch")
                .description("Total config fetch requests")
                .register(registry);
        this.configHit304 = Counter.builder("victor.config.hit")
                .description("Config fetch 304 (no change) responses")
                .register(registry);
        this.eventsIngested = Counter.builder("victor.events.ingested")
                .description("Total events ingested")
                .register(registry);
        this.eventsIngestionErrors = Counter.builder("victor.events.ingestion.errors")
                .description("Event ingestion errors")
                .register(registry);
        this.clickhouseQueryDuration = Timer.builder("victor.clickhouse.query")
                .description("ClickHouse query duration")
                .register(registry);
        this.experimentTransitions = Counter.builder("victor.experiment.transitions")
                .description("Experiment state transitions")
                .register(registry);
        this.kafkaConsumerLag = Timer.builder("victor.kafka.consumer.lag")
                .description("Kafka consumer processing lag")
                .register(registry);
    }

    // ---- Bucketing ----
    public void recordBucketingRequest(long durationMs) {
        bucketingRequests.increment();
        bucketingDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ---- Config ----
    public void recordConfigFetch(boolean hit) {
        configFetchTotal.increment();
        if (hit) {
            configHit304.increment();
        }
    }

    // ---- Event Ingestion ----
    public void recordEventIngested() {
        eventsIngested.increment();
    }

    public void recordEventIngestionError() {
        eventsIngestionErrors.increment();
    }

    // ---- ClickHouse ----
    public void recordClickHouseQuery(long durationMs) {
        clickhouseQueryDuration.record(durationMs, TimeUnit.MILLISECONDS);
    }

    // ---- Experiment Lifecycle ----
    public void recordExperimentTransition(String from, String to) {
        experimentTransitions.increment();
    }

    // ---- Kafka Consumer ----
    public void recordKafkaConsumerLag(long lagMs) {
        kafkaConsumerLag.record(lagMs, TimeUnit.MILLISECONDS);
    }
}
