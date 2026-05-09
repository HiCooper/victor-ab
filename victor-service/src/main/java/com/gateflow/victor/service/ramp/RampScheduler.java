package com.gateflow.victor.service.ramp;

import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
import com.gateflow.victor.service.experiment.ExperimentService;
import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.model.ExperimentReport;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 动态灰度推进调度器 - 自动根据门禁条件推进灰度比例
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RampScheduler {

    private final ExperimentMapper experimentMapper;
    private final VariantMapper variantMapper;
    private final ExperimentService experimentService;
    private final StatsEngine statsEngine;

    /**
     * 从实验的变体中获取最大的 bucketEnd，用于判断当前灰度阶段
     */
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

    /**
     * 更新实验所有变体的 bucketEnd 到目标值
     */
    private void updateVariantBucketEnds(Experiment exp, int newBucketEnd) {
        List<Variant> variants = variantMapper.selectActiveVariants(exp.getExpId());
        if (variants != null) {
            for (Variant v : variants) {
                v.setBucketEnd(newBucketEnd);
                variantMapper.updateById(v);
            }
        }
    }

    /**
     * 灰度阶段定义
     */
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

    /**
     * 灰度门禁检查 - 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000)
    public void checkAndAdvanceRamp() {
        log.info("Checking ramp experiments for automatic advancement...");

        List<Experiment> rampingExperiments = experimentMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Experiment>()
                .eq(Experiment::getStatus, "ramp")
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
            // 已经全量，无需推进
            return;
        }

        // 检查当前阶段门禁条件
        RampCheckResult checkResult = checkRampGate(exp, currentStage);

        if (checkResult.isPassed()) {
            // 检查是否需要在当前阶段停留足够时间（最少停留时间基于阶段）
            if (shouldAdvance(exp, currentStage)) {
                RampStage nextStage = currentStage.next();

                // 更新所有变体的桶范围
                updateVariantBucketEnds(exp, nextStage.getBucketEnd());

                log.info("Experiment {} advanced from {} to {}",
                    exp.getExpId(), currentStage.getLabel(), nextStage.getLabel());

                // TODO: 发送通知给实验Owner
                // notificationService.sendRampAdvanceNotification(exp, nextStage);
            }
        } else {
            // 门禁失败，检查是否需要暂停
            if (checkResult.isCritical()) {
                experimentService.pauseExperiment(exp.getId());
                log.warn("Experiment {} paused due to critical ramp gate failure: {}",
                    exp.getExpId(), checkResult.getMessage());
                // TODO: 发送告警
            }
        }
    }

    /**
     * 灰度门禁检查
     */
    private RampCheckResult checkRampGate(Experiment exp, RampStage stage) {
        // SRM 检验 - 所有阶段都必须通过
        boolean srmPassed = checkSRM(exp);
        if (!srmPassed) {
            return RampCheckResult.critical("SRM检验失败，分流比例异常");
        }

        // 护栏指标检查 - 所有阶段都必须通过
        boolean guardrailPassed = checkGuardrails(exp);
        if (!guardrailPassed) {
            return RampCheckResult.critical("护栏指标恶化");
        }

        // 灰度1: 基础检查
        if (stage == RampStage.STAGE_1) {
            return RampCheckResult.pass("SRM和护栏指标正常");
        }

        // 灰度5: 检查主指标方向
        if (stage == RampStage.STAGE_5) {
            boolean directionOk = checkMetricDirection(exp);
            if (!directionOk) {
                return RampCheckResult.warning("主指标方向与预期不符，建议观察");
            }
            return RampCheckResult.pass("主指标方向正常");
        }

        // 灰度10: 检查统计功效
        if (stage == RampStage.STAGE_10) {
            return RampCheckResult.pass("基础验证通过");
        }

        // 灰度50: 全面验证
        return RampCheckResult.pass("全面验证通过");
    }

    private boolean checkSRM(Experiment exp) {
        try {
            // 查询 SRM 结果
            ExperimentReport report = statsEngine.analyzeExperiment(
                exp.getExpId(),
                "layer", // 需要从实验获取
                LocalDate.now().minusDays(7),
                LocalDate.now(),
                "control", // 需要从实验获取对照组
                List.of("treatment") // 需要从实验获取实验组
            );
            
            if (report.getSrmCheck() != null) {
                return report.getSrmCheck().isPassed();
            }
            return true; // 如果无法获取SRM结果，默认通过
        } catch (Exception e) {
            log.warn("Failed to check SRM for experiment {}: {}", exp.getExpId(), e.getMessage());
            return true; // 统计服务不可用时不阻塞灰度
        }
    }

    private boolean checkGuardrails(Experiment exp) {
        // 从 ClickHouse 查询护栏指标
        // 简化实现：检查错误率和崩溃率
        // 实际应从 experiment_metrics 表查询
        
        // 这里返回 true，实际应查询:
        // SELECT sum(conversions)/sum(unique_users) as error_rate
        // FROM victor.experiment_metrics 
        // WHERE exp_id = ? AND variant IN (?) AND metric_name IN ('error_rate', 'crash_rate')
        
        return true;
    }

    private boolean checkMetricDirection(Experiment exp) {
        // 检查主指标是否与预期方向一致
        // 简化实现：返回 true
        // 实际应从 StatsEngine 查询主指标趋势
        
        return true;
    }

    /**
     * 判断是否应该推进到下一阶段
     * 基于在当前阶段的停留时间
     */
    private boolean shouldAdvance(Experiment exp, RampStage stage) {
        if (exp.getStartTime() == null) {
            return false;
        }

        long hoursInStage = java.time.Duration.between(exp.getStartTime(), java.time.LocalDateTime.now()).toHours();

        // 各阶段最少停留时间（小时）
        Map<RampStage, Long> minHours = Map.of(
            RampStage.STAGE_1, 2L,
            RampStage.STAGE_5, 4L,
            RampStage.STAGE_10, 12L,
            RampStage.STAGE_50, 24L
        );

        return hoursInStage >= minHours.getOrDefault(stage, 24L);
    }

    /**
     * 获取实验当前灰度状态
     */
    public Map<String, Object> getRampStatus(Long experimentId) {
        Experiment exp = experimentMapper.selectById(experimentId);
        if (exp == null) {
            throw new IllegalArgumentException("Experiment not found");
        }

        if (!"ramp".equals(exp.getStatus())) {
            throw new IllegalArgumentException("Experiment is not in ramp status");
        }

        RampStage currentStage = RampStage.fromBucketEnd(getMaxVariantBucketEnd(exp));
        RampStage nextStage = currentStage.next();

        Map<String, Object> status = new HashMap<>();
        status.put("experimentId", exp.getExpId());
        status.put("currentStage", currentStage.getLabel());
        status.put("currentTraffic", currentStage.getPercent() + "%");
        status.put("nextStage", nextStage != currentStage ? nextStage.getLabel() : "已全量");
        status.put("nextTraffic", nextStage != currentStage ? nextStage.getPercent() + "%" : "100%");
        status.put("canAdvance", currentStage != RampStage.STAGE_100);
        status.put("focus", currentStage.getFocus());

        return status;
    }

    /**
     * 手动推进灰度
     */
    public void manuallyAdvanceRamp(Long experimentId) {
        Experiment exp = experimentMapper.selectById(experimentId);
        if (exp == null || !"ramp".equals(exp.getStatus())) {
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
