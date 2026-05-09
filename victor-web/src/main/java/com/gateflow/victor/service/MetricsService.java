package com.gateflow.victor.service;

import com.gateflow.victor.stats.repository.MetricsRepository;
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
     * Get daily metrics for an experiment
     */
    public List<Map<String, Object>> getDailyMetrics(String expId, String startDate, String endDate) {
        log.info("Getting daily metrics for experiment: {}, start: {}, end: {}", expId, startDate, endDate);
        
        List<Map<String, Object>> dailyMetrics = new ArrayList<>();
        
        // Generate mock daily data for the past 7 days
        LocalDate today = LocalDate.now();
        Random random = new Random(42); // Fixed seed for consistent demo data
        
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            
            // Control metrics
            Map<String, Object> controlMetrics = new LinkedHashMap<>();
            controlMetrics.put("date", date.toString());
            controlMetrics.put("expId", expId);
            controlMetrics.put("variant", "control");
            controlMetrics.put("sampleSize", 8000 + random.nextInt(2000));
            controlMetrics.put("conversions", 180 + random.nextInt(50));
            controlMetrics.put("conversionRate", 0.022 + random.nextDouble() * 0.008);
            controlMetrics.put("revenue", 2200.0 + random.nextInt(500));
            dailyMetrics.add(controlMetrics);
            
            // Treatment metrics
            Map<String, Object> treatmentMetrics = new LinkedHashMap<>();
            treatmentMetrics.put("date", date.toString());
            treatmentMetrics.put("expId", expId);
            treatmentMetrics.put("variant", "treatment");
            treatmentMetrics.put("sampleSize", 8000 + random.nextInt(2000));
            treatmentMetrics.put("conversions", 220 + random.nextInt(60));
            treatmentMetrics.put("conversionRate", 0.027 + random.nextDouble() * 0.008);
            treatmentMetrics.put("revenue", 2800.0 + random.nextInt(600));
            dailyMetrics.add(treatmentMetrics);
        }
        
        return dailyMetrics;
    }

    /**
     * Get dashboard statistics
     */
    public Map<String, Object> getDashboardStats() {
        log.info("Getting dashboard statistics");
        
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalExperiments", 156);
        stats.put("activeExperiments", 12);
        stats.put("completedThisWeek", 8);
        stats.put("significantPositive", 5);
        stats.put("significantNegative", 2);
        stats.put("pendingReports", 3);
        
        return stats;
    }
}
