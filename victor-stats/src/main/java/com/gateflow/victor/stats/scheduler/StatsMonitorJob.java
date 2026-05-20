package com.gateflow.victor.stats.scheduler;

import com.gateflow.victor.stats.algorithm.SrmTest;
import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.repository.ExperimentRepository;
import com.gateflow.victor.stats.repository.MetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * StatsMonitorJob - 每5分钟轻量健康检查
 * 仅执行 SRM 检验 + 转化率快照，结果写入 Redis 供 dashboard 消费
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatsMonitorJob {

    private final ExperimentRepository experimentRepository;
    private final StatsEngine statsEngine;
    private final StringRedisTemplate redis;

    private static final int MONITOR_TTL_SECONDS = 600; // 10 min TTL

    @Scheduled(fixedRate = 300_000)
    public void runMonitor() {
        log.debug("Starting monitor cycle...");

        List<ExperimentRepository.ExperimentSummary> experiments =
            experimentRepository.findRunningExperiments();

        for (ExperimentRepository.ExperimentSummary exp : experiments) {
            try {
                monitorExperiment(exp);
            } catch (Exception e) {
                log.error("Monitor failed for experiment {}: {}", exp.getExpId(), e.getMessage());
            }
        }

        log.debug("Monitor cycle complete. Checked {} experiments.", experiments.size());
    }

    private void monitorExperiment(ExperimentRepository.ExperimentSummary exp) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(1); // last 24 hours

        // 1. Query aggregated stats from ClickHouse
        Map<String, MetricsRepository.VariantStats> variantStats =
            statsEngine.getMetricsRepository().queryExperimentStats(exp.getExpId(), startDate, endDate);

        if (variantStats.isEmpty()) {
            return;
        }

        // 2. SRM check
        ExperimentRepository.VariantInfo variants = experimentRepository.findVariants(exp.getExpId());
        List<String> allVariants = variants.getAllVariantKeys();

        long[] observed = new long[allVariants.size()];
        double[] expected = new double[allVariants.size()];
        Map<String, Long> userCounts = new LinkedHashMap<>();

        long totalUsers = 0;
        for (int i = 0; i < allVariants.size(); i++) {
            MetricsRepository.VariantStats vs = variantStats.get(allVariants.get(i));
            long users = vs != null ? vs.getTotalUsers() : 0L;
            observed[i] = users;
            userCounts.put(allVariants.get(i), users);
            totalUsers += users;
        }
        Arrays.fill(expected, 1.0 / expected.length);

        double srmPValue = SrmTest.chiSquareTest(observed, expected);
        boolean srmPassed = srmPValue >= 0.01;

        if (!srmPassed) {
            log.warn("SRM check FAILED for experiment {}: p-value={}", exp.getExpId(), srmPValue);
        }

        // 3. Conversion rate snapshot per variant
        List<Map<String, Object>> variantSnapshots = new ArrayList<>();
        for (String variant : allVariants) {
            MetricsRepository.VariantStats vs = variantStats.get(variant);
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("variant", variant);
            snap.put("users", vs != null ? vs.getTotalUsers() : 0L);
            snap.put("conversions", vs != null ? vs.getTotalConversions() : 0L);
            snap.put("conversionRate", vs != null ? vs.getConversionRate() : 0.0);
            variantSnapshots.add(snap);
        }

        // 4. Write to Redis
        String redisKey = "stats:monitor:" + exp.getExpId();
        Map<String, String> redisData = new LinkedHashMap<>();
        redisData.put("expId", exp.getExpId());
        redisData.put("srmPassed", String.valueOf(srmPassed));
        redisData.put("srmPValue", String.format("%.6f", srmPValue));
        redisData.put("totalUsers", String.valueOf(totalUsers));

        // Store variant snapshots as individual hash fields
        for (int i = 0; i < variantSnapshots.size(); i++) {
            Map<String, Object> snap = variantSnapshots.get(i);
            String variantKey = (String) snap.get("variant");
            redisData.put("variant:" + variantKey + ":users", String.valueOf(snap.get("users")));
            redisData.put("variant:" + variantKey + ":conversions", String.valueOf(snap.get("conversions")));
            redisData.put("variant:" + variantKey + ":conversionRate", String.format("%.4f", snap.get("conversionRate")));
        }

        redis.opsForHash().putAll(redisKey, redisData);
        redis.expire(redisKey, Duration.ofSeconds(MONITOR_TTL_SECONDS));

        log.debug("Monitor snapshot written to Redis for experiment {}", exp.getExpId());
    }
}
