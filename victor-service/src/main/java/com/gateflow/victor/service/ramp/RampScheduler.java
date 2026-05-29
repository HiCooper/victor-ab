package com.gateflow.victor.service.ramp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
import com.gateflow.victor.service.experiment.ExperimentService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 灰度自动推进调度器
 * 从 Redis 读取 StatsMonitorJob 发布的健康检查结果，结合时间门槛决定是否推进。
 * 仅处理 auto_ramp_enabled=true 的实验。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RampScheduler {

    private final ExperimentMapper experimentMapper;
    private final VariantMapper variantMapper;
    private final ExperimentService experimentService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private static final Map<RampStage, Long> DEFAULT_STAGE_HOURS = new LinkedHashMap<>();
    static {
        DEFAULT_STAGE_HOURS.put(RampStage.STAGE_1, 2L);
        DEFAULT_STAGE_HOURS.put(RampStage.STAGE_5, 4L);
        DEFAULT_STAGE_HOURS.put(RampStage.STAGE_10, 12L);
        DEFAULT_STAGE_HOURS.put(RampStage.STAGE_50, 24L);
    }

    private int getMaxVariantBucketEnd(Experiment exp) {
        List<Variant> variants = variantMapper.selectActiveVariants(exp.getExpId());
        if (variants == null || variants.isEmpty()) {
            return 0;
        }
        return variants.stream()
            .filter(v -> v.getBucketEnd() != null)
            .mapToInt(Variant::getBucketEnd)
            .max()
            .orElse(0);
    }

    private void updateVariantBucketEnds(Experiment exp, int newBucketEnd) {
        List<Variant> variants = variantMapper.selectActiveVariants(exp.getExpId());
        if (variants != null) {
            for (Variant v : variants) {
                v.setBucketEnd(newBucketEnd);
                variantMapper.updateById(v);
            }
        }
    }

    public enum RampStage {
        STAGE_1(1, 100, "灰度1%", "SRM检验、核心流程可用性"),
        STAGE_5(5, 500, "灰度5%", "护栏指标趋势、主指标初步方向"),
        STAGE_10(10, 1000, "灰度10%", "统计功效达标检查"),
        STAGE_50(50, 5000, "灰度50%", "全面验证"),
        STAGE_100(100, 9999, "全量发布", "实验完成");

        private final int percent;
        private final int bucketEnd;
        private final String label;
        private final String focus;

        RampStage(int percent, int bucketEnd, String label, String focus) {
            this.percent = percent;
            this.bucketEnd = bucketEnd;
            this.label = label;
            this.focus = focus;
        }

        public int getPercent() { return percent; }
        public int getBucketEnd() { return bucketEnd; }
        public String getLabel() { return label; }
        public String getFocus() { return focus; }

        public RampStage next() {
            if (this == STAGE_1) return STAGE_5;
            if (this == STAGE_5) return STAGE_10;
            if (this == STAGE_10) return STAGE_50;
            if (this == STAGE_50) return STAGE_100;
            return this;
        }

        public static RampStage fromBucketEnd(int bucketEnd) {
            if (bucketEnd < 200) return STAGE_1;
            if (bucketEnd < 600) return STAGE_5;
            if (bucketEnd < 1100) return STAGE_10;
            if (bucketEnd < 5100) return STAGE_50;
            return STAGE_100;
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void checkAndAdvanceRamp() {
        log.info("Checking auto-ramp experiments...");

        List<Experiment> rampingExperiments = experimentMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Experiment>()
                .eq(Experiment::getStatus, ExperimentStatus.RUNNING.getCode())
                .eq(Experiment::getAutoRampEnabled, true)
        );

        for (Experiment exp : rampingExperiments) {
            try {
                processRampExperiment(exp);
            } catch (Exception e) {
                log.error("Error processing ramp experiment {}: {}", exp.getExpId(), e.getMessage(), e);
            }
        }
    }

    private void processRampExperiment(Experiment exp) {
        int maxBucketEnd = getMaxVariantBucketEnd(exp);
        RampStage currentStage = RampStage.fromBucketEnd(maxBucketEnd);

        if (currentStage == RampStage.STAGE_100) {
            return;
        }

        RampCheckResult checkResult = checkRampGate(exp, currentStage);

        if (checkResult.isPassed() && shouldAdvance(exp, currentStage)) {
            RampStage nextStage = currentStage.next();
            updateVariantBucketEnds(exp, nextStage.getBucketEnd());

            log.info("Experiment {} advanced from {} to {}",
                exp.getExpId(), currentStage.getLabel(), nextStage.getLabel());
        } else if (checkResult.isCritical()) {
            experimentService.stopExperiment(exp.getId());
            log.warn("Experiment {} paused due to critical gate failure: {}",
                exp.getExpId(), checkResult.getMessage());
        }
    }

    /**
     * 从 Redis 读取 StatsMonitorJob 发布的门禁检查结果。
     * 不再独立调用 statsEngine，消除重复计算。
     */
    private RampCheckResult checkRampGate(Experiment exp, RampStage stage) {
        Map<String, String> monitor = readMonitorData(exp.getExpId());

        if (monitor.isEmpty()) {
            return RampCheckResult.pass("no monitor data yet");
        }

        // SRM 检查 — 所有阶段必须通过
        boolean srmPassed = "true".equals(monitor.get("srmPassed"));
        if (!srmPassed) {
            return RampCheckResult.critical("SRM检验失败，分流比例异常");
        }

        // 护栏指标 — 所有阶段必须通过
        boolean guardrailsPassed = "true".equals(monitor.get("guardrailsPassed"));
        if (!guardrailsPassed) {
            return RampCheckResult.critical("护栏指标恶化");
        }

        // 灰度1%: 基础检查通过即可
        if (stage == RampStage.STAGE_1) {
            return RampCheckResult.pass("SRM和护栏指标正常");
        }

        // 灰度5%: 检查主指标方向
        if (stage == RampStage.STAGE_5) {
            String direction = monitor.getOrDefault("primaryDirection", "neutral");
            if ("negative".equals(direction)) {
                return RampCheckResult.warning("主指标方向为负，建议观察");
            }
            return RampCheckResult.pass("主指标方向正常");
        }

        // 灰度10%+: 已通过前两个阶段验证
        return RampCheckResult.pass("门禁通过");
    }

    private Map<String, String> readMonitorData(String expId) {
        try {
            Map<Object, Object> raw = redis.opsForHash().entries("stats:monitor:" + expId);
            Map<String, String> result = new HashMap<>();
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                result.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to read monitor data for {}: {}", expId, e.getMessage());
            return Map.of();
        }
    }

    private boolean shouldAdvance(Experiment exp, RampStage stage) {
        if (exp.getStartTime() == null) {
            return false;
        }

        long hoursInStage = Duration.between(exp.getStartTime(), LocalDateTime.now()).toHours();
        Map<RampStage, Long> minHours = parseRampConfig(exp.getRampConfig());

        return hoursInStage >= minHours.getOrDefault(stage, 24L);
    }

    private Map<RampStage, Long> parseRampConfig(String rampConfigJson) {
        if (rampConfigJson == null || rampConfigJson.isBlank()) {
            return DEFAULT_STAGE_HOURS;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(rampConfigJson,
                new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            Map<String, Object> stages = (Map<String, Object>) config.get("stages");
            if (stages == null) {
                return DEFAULT_STAGE_HOURS;
            }

            Map<RampStage, Long> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : stages.entrySet()) {
                try {
                    RampStage stage = RampStage.valueOf(entry.getKey());
                    long hours = ((Number) entry.getValue()).longValue();
                    result.put(stage, hours);
                } catch (IllegalArgumentException ignored) {
                    // Unknown stage name in JSON, skip
                }
            }
            return result.isEmpty() ? DEFAULT_STAGE_HOURS : result;
        } catch (Exception e) {
            log.warn("Failed to parse ramp_config for experiment: {}", e.getMessage());
            return DEFAULT_STAGE_HOURS;
        }
    }

    public Map<String, Object> getRampStatus(Long experimentId) {
        Experiment exp = experimentMapper.selectById(experimentId);
        if (exp == null) {
            throw new IllegalArgumentException("Experiment not found");
        }
        if (!ExperimentStatus.RUNNING.getCode().equals(exp.getStatus())) {
            throw new IllegalArgumentException("Experiment is not running");
        }

        RampStage currentStage = RampStage.fromBucketEnd(getMaxVariantBucketEnd(exp));
        RampStage nextStage = currentStage.next();

        Map<String, Object> status = new HashMap<>();
        status.put("experimentId", exp.getExpId());
        status.put("currentStage", currentStage.getLabel());
        status.put("currentTraffic", currentStage.getPercent() + "%");
        status.put("nextStage", nextStage != currentStage ? nextStage.getLabel() : "已全量");
        status.put("autoRampEnabled", exp.getAutoRampEnabled());
        status.put("focus", currentStage.getFocus());
        return status;
    }

    public void manuallyAdvanceRamp(Long experimentId) {
        Experiment exp = experimentMapper.selectById(experimentId);
        if (exp == null || !ExperimentStatus.RUNNING.getCode().equals(exp.getStatus())) {
            throw new IllegalArgumentException("Invalid experiment or status");
        }

        RampStage currentStage = RampStage.fromBucketEnd(getMaxVariantBucketEnd(exp));
        if (currentStage == RampStage.STAGE_100) {
            throw new IllegalStateException("Experiment is already at 100%");
        }

        RampStage nextStage = currentStage.next();
        updateVariantBucketEnds(exp, nextStage.getBucketEnd());

        log.info("Manually advanced experiment {} to {}", exp.getExpId(), nextStage.getLabel());
    }

    @Data
    private static class RampCheckResult {
        private boolean passed;
        private boolean critical;
        private String message;

        public static RampCheckResult pass(String msg) {
            RampCheckResult r = new RampCheckResult();
            r.passed = true;
            r.critical = false;
            r.message = msg;
            return r;
        }

        public static RampCheckResult critical(String msg) {
            RampCheckResult r = new RampCheckResult();
            r.passed = false;
            r.critical = true;
            r.message = msg;
            return r;
        }

        public static RampCheckResult warning(String msg) {
            RampCheckResult r = new RampCheckResult();
            r.passed = false;
            r.critical = false;
            r.message = msg;
            return r;
        }
    }
}
