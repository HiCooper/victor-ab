package com.gateflow.victor.service.statistics;

import com.gateflow.victor.stats.repository.MetricsRepository;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.domain.entity.Experiment;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for accessing experiment metrics
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MetricsRepository metricsRepository;
    private final ExperimentMapper experimentMapper;

    /**
     * Get real-time metrics for an experiment
     */
    public List<Map<String, Object>> getRealtimeMetrics(String expId, Integer windowMinutes) {
        log.info("Getting realtime metrics for experiment: {}, window: {} minutes", expId, windowMinutes);

        Map<String, MetricsRepository.VariantStats> stats = metricsRepository.queryRealtimeStats(expId, windowMinutes);

        List<Map<String, Object>> metrics = new ArrayList<>();
        for (Map.Entry<String, MetricsRepository.VariantStats> entry : stats.entrySet()) {
            MetricsRepository.VariantStats variantStats = entry.getValue();
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("expId", expId);
            metric.put("variant", variantStats.getVariant());
            metric.put("timestamp", LocalDateTime.now().toString());
            metric.put("totalEvents", variantStats.getTotalEvents());
            metric.put("uniqueUsers", variantStats.getTotalUsers());
            metric.put("conversions", variantStats.getTotalConversions());
            metric.put("conversionRate", variantStats.getConversionRate());
            metric.put("totalRevenue", variantStats.getTotalRevenue());
            metrics.add(metric);
        }

        return metrics;
    }

    /**
     * Get real-time trend (minute-level time series) for an experiment.
     */
    public List<Map<String, Object>> getRealtimeTrend(String expId, int hours) {
        log.info("Getting realtime trend for experiment: {}, hours: {}", expId, hours);
        return metricsRepository.queryRealtimeTrend(expId, hours);
    }

    /**
     * Get daily metrics for an experiment from ClickHouse
     */
    public List<Map<String, Object>> getDailyMetrics(String expId, String startDateStr, String endDateStr) {
        log.info("Getting daily metrics for experiment: {}, start: {}, end: {}", expId, startDateStr, endDateStr);

        LocalDate start = (startDateStr != null && !startDateStr.isEmpty())
            ? LocalDate.parse(startDateStr)
            : LocalDate.now().minusDays(7);
        LocalDate end = (endDateStr != null && !endDateStr.isEmpty())
            ? LocalDate.parse(endDateStr)
            : LocalDate.now();

        // Discover variants from experiment stats
        Map<String, MetricsRepository.VariantStats> variantStats =
            metricsRepository.queryExperimentStats(expId, start, end);

        List<Map<String, Object>> dailyMetrics = new ArrayList<>();
        for (String variant : variantStats.keySet()) {
            Map<LocalDate, MetricsRepository.DailyStats> trend =
                metricsRepository.queryDailyTrend(expId, variant, start, end);

            for (Map.Entry<LocalDate, MetricsRepository.DailyStats> entry : trend.entrySet()) {
                MetricsRepository.DailyStats ds = entry.getValue();
                Map<String, Object> metric = new LinkedHashMap<>();
                metric.put("date", entry.getKey().toString());
                metric.put("expId", expId);
                metric.put("variant", variant);
                metric.put("sampleSize", ds.getTotalUsers());
                metric.put("conversions", ds.getTotalConversions());
                metric.put("conversionRate", ds.getConversionRate());
                metric.put("revenue", ds.getTotalRevenue());
                dailyMetrics.add(metric);
            }
        }

        return dailyMetrics;
    }

    /**
     * Get dashboard statistics from real data sources
     */
    public Map<String, Object> getDashboardStats() {
        log.info("Getting dashboard statistics");

        long totalExperiments = experimentMapper.selectCount(null);

        LambdaQueryWrapper<Experiment> activeWrapper = new LambdaQueryWrapper<>();
        activeWrapper.in(Experiment::getStatus, List.of("running"));
        long activeExperiments = experimentMapper.selectCount(activeWrapper);

        // Experiments completed this week (status = stopped/archive, updated in last 7 days)
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        LambdaQueryWrapper<Experiment> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.in(Experiment::getStatus, List.of("stopped", "archive"))
            .ge(Experiment::getUpdatedAt, weekAgo.atStartOfDay());
        long completedThisWeek = experimentMapper.selectCount(completedWrapper);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalExperiments", totalExperiments);
        stats.put("activeExperiments", activeExperiments);
        stats.put("completedThisWeek", completedThisWeek);
        stats.put("significantPositive", 0);  // Requires ClickHouse aggregation — placeholder
        stats.put("significantNegative", 0);  // Requires ClickHouse aggregation — placeholder
        stats.put("pendingReports", 0);       // Requires report job tracking — placeholder

        return stats;
    }

    /**
     * Get event details for an experiment
     */
    public List<Map<String, Object>> getEventDetails(String expId, int limit, int offset) {
        log.info("Getting event details for experiment: {}, limit: {}, offset: {}", expId, limit, offset);
        return metricsRepository.queryEventDetails(expId, limit, offset);
    }
}
