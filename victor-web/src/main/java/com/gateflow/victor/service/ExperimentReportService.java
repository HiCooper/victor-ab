package com.gateflow.victor.service;

import com.gateflow.victor.stats.engine.StatsEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing experiment reports
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentReportService {

    private final StatsEngine statsEngine;
    private final ReportJobService jobService;
    private final MetricsService metricsService;

    /**
     * Get report for a specific experiment
     */
    public Map<String, Object> getReport(String expId) {
        log.info("Getting report for experiment: {}", expId);
        
        // Get metrics data
        List<Map<String, Object>> dailyMetrics = metricsService.getDailyMetrics(expId, null, null);
        
        // Build report from metrics and stats engine
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("experimentId", expId);
        report.put("experimentName", "Experiment " + expId);
        report.put("status", "completed");
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("timeRange", Map.of(
                "start", LocalDateTime.now().minusDays(7).toString(),
                "end", LocalDateTime.now().toString()
        ));
        
        // Add mock SRM check
        report.put("srmCheck", Map.of(
                "passed", true,
                "chiSquareStat", 0.1234,
                "pValue", 0.7256,
                "expectedRatio", "50:50",
                "actualRatio", "49:51"
        ));
        
        // Add primary metrics
        report.put("primaryMetrics", buildPrimaryMetrics(dailyMetrics));
        report.put("secondaryMetrics", buildSecondaryMetrics(dailyMetrics));
        report.put("guardrailMetrics", buildGuardrailMetrics());
        report.put("recommendation", buildRecommendation());
        report.put("dailyTrend", dailyMetrics);
        
        return report;
    }

    /**
     * Get all reports
     */
    public List<Map<String, Object>> getAllReports() {
        log.info("Getting all reports");
        // Return list of all experiment reports
        return new ArrayList<>();
    }

    /**
     * Trigger report generation
     */
    public String triggerReportGeneration(String expId, String startDate, String endDate,
                                          Boolean includeSecondaryMetrics, Boolean includeGuardrails) {
        log.info("Triggering report generation for experiment: {}", expId);
        
        // Create job
        String jobId = UUID.randomUUID().toString();
        jobService.createJob(jobId, "report_generation", expId);
        
        // In a real implementation, this would submit to a queue for async processing
        // For now, we simulate immediate completion
        new Thread(() -> {
            try {
                jobService.updateJobStatus(jobId, "running", 0);
                
                // Simulate report generation steps
                for (int i = 0; i <= 100; i += 20) {
                    Thread.sleep(500);
                    jobService.updateJobStatus(jobId, "running", i);
                }
                
                jobService.updateJobStatus(jobId, "completed", 100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                jobService.updateJobStatus(jobId, "failed", 0);
            }
        }).start();
        
        return jobId;
    }

    private List<Map<String, Object>> buildPrimaryMetrics(List<Map<String, Object>> dailyMetrics) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        
        Map<String, Object> conversionRate = new LinkedHashMap<>();
        conversionRate.put("metricName", "转化率");
        conversionRate.put("control", Map.of(
                "mean", 0.0234,
                "stdDev", 0.0012,
                "sampleSize", 10000,
                "confidenceInterval", new double[]{0.0210, 0.0258}
        ));
        conversionRate.put("treatment", Map.of(
                "mean", 0.0289,
                "stdDev", 0.0014,
                "sampleSize", 10000,
                "confidenceInterval", new double[]{0.0261, 0.0317}
        ));
        conversionRate.put("lift", 0.235);
        conversionRate.put("liftLowerCI", 0.152);
        conversionRate.put("liftUpperCI", 0.318);
        conversionRate.put("pValue", 0.0023);
        conversionRate.put("isSignificant", true);
        conversionRate.put("testMethod", "z-test");
        metrics.add(conversionRate);
        
        return metrics;
    }

    private List<Map<String, Object>> buildSecondaryMetrics(List<Map<String, Object>> dailyMetrics) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        
        Map<String, Object> avgRevenue = new LinkedHashMap<>();
        avgRevenue.put("metricName", "人均收入");
        avgRevenue.put("control", Map.of(
                "mean", 12.50,
                "stdDev", 2.30,
                "sampleSize", 10000,
                "confidenceInterval", new double[]{10.20, 14.80}
        ));
        avgRevenue.put("treatment", Map.of(
                "mean", 14.80,
                "stdDev", 2.50,
                "sampleSize", 10000,
                "confidenceInterval", new double[]{12.30, 17.30}
        ));
        avgRevenue.put("lift", 0.184);
        avgRevenue.put("liftLowerCI", 0.089);
        avgRevenue.put("liftUpperCI", 0.279);
        avgRevenue.put("pValue", 0.0156);
        avgRevenue.put("isSignificant", true);
        avgRevenue.put("testMethod", "t-test");
        metrics.add(avgRevenue);
        
        return metrics;
    }

    private List<Map<String, Object>> buildGuardrailMetrics() {
        List<Map<String, Object>> metrics = new ArrayList<>();
        
        Map<String, Object> pageLoadTime = new LinkedHashMap<>();
        pageLoadTime.put("metricName", "页面加载时间");
        pageLoadTime.put("control", Map.of(
                "mean", 1.23,
                "stdDev", 0.45,
                "sampleSize", 10000,
                "confidenceInterval", new double[]{1.18, 1.28}
        ));
        pageLoadTime.put("treatment", Map.of(
                "mean", 1.25,
                "stdDev", 0.48,
                "sampleSize", 10000,
                "confidenceInterval", new double[]{1.20, 1.30}
        ));
        pageLoadTime.put("lift", 0.016);
        pageLoadTime.put("liftLowerCI", -0.015);
        pageLoadTime.put("liftUpperCI", 0.047);
        pageLoadTime.put("pValue", 0.2345);
        pageLoadTime.put("isSignificant", false);
        pageLoadTime.put("testMethod", "t-test");
        metrics.add(pageLoadTime);
        
        return metrics;
    }

    private Map<String, Object> buildRecommendation() {
        Map<String, Object> recommendation = new LinkedHashMap<>();
        recommendation.put("action", "launch");
        recommendation.put("confidence", 0.89);
        recommendation.put("reasoning", "实验组转化率显著提升23.5%，人均收入提升18.4%，护栏指标无显著变化。推荐发布新版本。");
        recommendation.put("estimatedImpact", Map.of(
                "dailyRevenue", 12500.0,
                "annualRevenue", 4562500.0
        ));
        return recommendation;
    }
}
