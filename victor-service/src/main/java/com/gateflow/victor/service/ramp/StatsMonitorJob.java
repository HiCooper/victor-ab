package com.gateflow.victor.service.ramp;

import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.model.ExperimentReport;
import com.gateflow.victor.stats.model.SequentialStatus;
import com.gateflow.victor.stats.model.SequentialTestResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Publishes per-experiment health check results to Redis for RampScheduler consumption.
 * Runs on a fixed schedule, slightly offset from RampScheduler to ensure fresh data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsMonitorJob {

    private final ExperimentMapper experimentMapper;
    private final VariantMapper variantMapper;
    private final LayerMapper layerMapper;
    private final StatsEngine statsEngine;
    private final StringRedisTemplate redis;

    @Scheduled(fixedRate = 240_000, initialDelay = 60_000)
    public void publishMonitorData() {
        log.debug("StatsMonitorJob: publishing ramp monitor data...");

        List<Experiment> rampingExperiments = experimentMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Experiment>()
                .eq(Experiment::getStatus, ExperimentStatus.RAMP.getCode())
                .eq(Experiment::getAutoRampEnabled, true)
        );

        for (Experiment exp : rampingExperiments) {
            try {
                publishExperimentMonitor(exp);
            } catch (Exception e) {
                log.error("Failed to publish monitor data for {}: {}", exp.getExpId(), e.getMessage());
            }
        }
    }

    private void publishExperimentMonitor(Experiment exp) {
        List<Variant> variants = variantMapper.selectActiveVariants(exp.getExpId());
        if (variants.isEmpty()) {
            return;
        }

        Layer layer = layerMapper.selectById(exp.getLayerId());
        String layerKey = layer != null ? layer.getLayerId() : "default";

        // Classify control vs treatment
        String controlVariant = null;
        List<String> treatmentVariants = new ArrayList<>();
        Map<String, Double> expectedProportions = new HashMap<>();
        int totalBucketSpan = variants.stream()
            .mapToInt(v -> v.getBucketEnd() - v.getBucketStart() + 1).sum();

        for (Variant v : variants) {
            String key = v.getBucketId() != null ? v.getBucketId() : v.getName();
            double proportion = totalBucketSpan > 0
                ? (double) (v.getBucketEnd() - v.getBucketStart() + 1) / totalBucketSpan
                : 1.0 / variants.size();
            expectedProportions.put(key, proportion);

            if ("control".equalsIgnoreCase(key) || controlVariant == null) {
                controlVariant = key;
            } else {
                treatmentVariants.add(key);
            }
        }

        LocalDate startDate = exp.getStartTime() != null
            ? exp.getStartTime().toLocalDate()
            : LocalDate.now().minusDays(7);
        LocalDate endDate = exp.getEndTime() != null
            ? exp.getEndTime().toLocalDate()
            : LocalDate.now();

        // Parse guardrail metric names from experiment config
        List<String> guardrailMetricNames = parseGuardrailMetrics(exp.getGuardrailMetrics());

        ExperimentReport report = statsEngine.analyzeExperiment(
            exp.getExpId(), layerKey, startDate, endDate,
            controlVariant, treatmentVariants, expectedProportions,
            guardrailMetricNames
        );

        // Write results to Redis hash
        Map<String, String> monitorData = new LinkedHashMap<>();

        boolean srmPassed = report.getSrmCheck() != null && report.getSrmCheck().isPassed();
        monitorData.put("srmPassed", String.valueOf(srmPassed));

        boolean guardrailsPassed = report.getGuardrailMetrics() == null
            || report.getGuardrailMetrics().stream()
                .noneMatch(g -> g.getStatus() == SequentialStatus.STOP_NEGATIVE);
        monitorData.put("guardrailsPassed", String.valueOf(guardrailsPassed));

        String direction = "neutral";
        if (report.getPrimaryMetric() != null && report.getPrimaryMetric().getLift() != null) {
            double lift = report.getPrimaryMetric().getLift().getValue();
            direction = lift > 0.01 ? "positive" : (lift < -0.01 ? "negative" : "neutral");
        }
        monitorData.put("primaryDirection", direction);

        if (report.getPrimaryMetric() != null) {
            monitorData.put("primaryPValue", String.valueOf(report.getPrimaryMetric().getPValue()));
            monitorData.put("primarySignificant", String.valueOf(report.getPrimaryMetric().isSignificant()));
        }

        String redisKey = "stats:monitor:" + exp.getExpId();
        redis.opsForHash().putAll(redisKey, monitorData);
        redis.expire(redisKey, java.time.Duration.ofMinutes(10));
        log.debug("Published monitor data for {}: srm={}, guardrails={}, direction={}",
            exp.getExpId(), srmPassed, guardrailsPassed, direction);
    }

    private List<String> parseGuardrailMetrics(String guardrailMetricsJson) {
        if (guardrailMetricsJson == null || guardrailMetricsJson.isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<?> raw = om.readValue(guardrailMetricsJson,
                new com.fasterxml.jackson.core.type.TypeReference<List<?>>() {});
            List<String> names = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof String s) {
                    names.add(s);
                } else if (item instanceof Map<?, ?> m && m.containsKey("name")) {
                    names.add(m.get("name").toString());
                }
            }
            return names.isEmpty() ? null : names;
        } catch (Exception e) {
            return null;
        }
    }
}
