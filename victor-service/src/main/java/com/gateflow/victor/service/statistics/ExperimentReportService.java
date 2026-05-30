package com.gateflow.victor.service.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.model.ExperimentReport;
import com.gateflow.victor.stats.model.SequentialTestResult;
import com.gateflow.victor.stats.model.TestResult;
import com.gateflow.victor.stats.repository.ReportRepository;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.BucketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentReportService {

    private final StatsEngine statsEngine;
    private final ReportJobService jobService;
    private final ReportRepository reportRepository;
    private final ExperimentMapper experimentMapper;
    private final BucketMapper bucketMapper;
    private final LayerMapper layerMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getReport(String expId) {
        log.info("Getting report for experiment: {}", expId);

        Experiment experiment = experimentMapper.selectByExpId(expId);
        if (experiment == null) {
            return buildEmptyReport(expId);
        }

        Layer layer = layerMapper.selectById(experiment.getLayerId());
        String layerKey = layer != null ? layer.getLayerId() : "default";

        List<Bucket> variants = bucketMapper.selectActiveBuckets(expId);
        if (variants.isEmpty()) {
            return buildEmptyReport(expId);
        }

        // Classify control vs treatment and compute expected proportions
        String controlVariant = null;
        List<String> treatmentVariants = new ArrayList<>();
        Map<String, Double> expectedProportions = new HashMap<>();
        int totalBucketSpan = variants.stream()
            .mapToInt(v -> v.getBucketEnd() - v.getBucketStart() + 1).sum();

        for (Bucket v : variants) {
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

        LocalDate startDate = experiment.getStartTime() != null
            ? experiment.getStartTime().toLocalDate()
            : LocalDate.now().minusDays(7);
        LocalDate endDate = experiment.getEndTime() != null
            ? experiment.getEndTime().toLocalDate()
            : LocalDate.now();

        List<String> guardrailMetricNames = parseGuardrailMetrics(experiment.getGuardrailMetrics());

        // Query behavior metrics from tracker (cross-database JOIN, may be slow)
        List<String> allVariantKeys = new ArrayList<>();
        allVariantKeys.add(controlVariant);
        allVariantKeys.addAll(treatmentVariants);
        Map<String, com.gateflow.victor.stats.repository.MetricsRepository.BehaviorMetrics> behaviorMap = new LinkedHashMap<>();
        try {
            behaviorMap = statsEngine.getMetricsRepository().queryBehaviorMetrics(expId, startDate, endDate, allVariantKeys);
            log.info("Behavior metrics for {}: {} variants, start={}, end={}",
                expId, behaviorMap.size(), startDate, endDate);
        } catch (Exception e) {
            log.warn("Behavior metrics query failed for {}: {}", expId, e.getMessage());
            for (String vk : allVariantKeys) behaviorMap.put(vk, new com.gateflow.victor.stats.repository.MetricsRepository.BehaviorMetrics(vk));
        }

        // For running experiments, build lightweight report without full stats pipeline
        if ("running".equals(experiment.getStatus())) {
            return buildRunningReport(expId, experiment, layerKey, variants, controlVariant,
                treatmentVariants, expectedProportions, behaviorMap, startDate, endDate);
        }

        ExperimentReport report = statsEngine.analyzeExperiment(
            expId, layerKey, startDate, endDate,
            controlVariant, treatmentVariants, expectedProportions,
            guardrailMetricNames
        );

        if (report.getVariantSummaries() != null && !behaviorMap.isEmpty()) {
            for (ExperimentReport.VariantSummary vs : report.getVariantSummaries().values()) {
                var bm = behaviorMap.get(vs.getVariant());
                if (bm != null) {
                    vs.setBehaviorMetrics(bm);
                }
            }
        }

        try {
            reportRepository.saveReport(report, endDate, report.isCupedApplied());
        } catch (Exception e) {
            log.warn("Failed to persist report for expId={}: {}", expId, e.getMessage());
        }

        return convertReportToMap(report, experiment);
    }

    public Map<String, Object> getHistoricalReport(String expId) {
        ExperimentReport report = reportRepository.findLatestReport(expId);
        if (report == null) {
            return buildEmptyReport(expId);
        }
        Experiment experiment = experimentMapper.selectByExpId(expId);
        return convertReportToMap(report, experiment != null ? experiment : new Experiment());
    }

    public Map<String, Object> getHistoricalReport(String expId, LocalDate date) {
        ExperimentReport report = reportRepository.findReportByDate(expId, date);
        if (report == null) {
            return Map.of("experimentId", expId, "status", "no_data", "message", "该日期无报告数据");
        }
        Experiment experiment = experimentMapper.selectByExpId(expId);
        return convertReportToMap(report, experiment != null ? experiment : new Experiment());
    }

    public List<LocalDate> getAvailableReportDates(String expId) {
        return reportRepository.getAvailableReportDates(expId);
    }

    public List<Map<String, Object>> getAllReports() {
        log.info("Getting all experiment reports");
        List<Experiment> experiments = experimentMapper.selectList(null);
        List<Map<String, Object>> reports = new ArrayList<>();
        for (Experiment exp : experiments) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("experimentId", exp.getExpId());
            summary.put("name", exp.getName());
            summary.put("status", exp.getStatus());
            summary.put("layerId", exp.getLayerId());
            summary.put("updatedAt", exp.getUpdatedAt() != null ? exp.getUpdatedAt().toString() : null);
            reports.add(summary);
        }
        return reports;
    }

    public String triggerReportGeneration(String expId, String startDate, String endDate,
                                          Boolean includeSecondaryMetrics, Boolean includeGuardrails) {
        log.info("Triggering report generation for experiment: {}", expId);

        String jobId = UUID.randomUUID().toString();
        jobService.createJob(jobId, "report_generation", expId);

        generateReportAsync(jobId, expId);

        return jobId;
    }

    @Async("reportTaskExecutor")
    protected void generateReportAsync(String jobId, String expId) {
        try {
            jobService.updateJobStatus(jobId, "running", 0);
            getReport(expId);
            jobService.updateJobStatus(jobId, "completed", 100);
        } catch (Exception e) {
            log.error("Report generation failed for expId={}", expId, e);
            jobService.updateJobStatus(jobId, "failed", 0);
        }
    }

    private Map<String, Object> buildRunningReport(String expId, Experiment experiment,
            String layerKey, List<Bucket> variants,
            String controlVariant, List<String> treatmentVariants,
            Map<String, Double> expectedProportions,
            Map<String, com.gateflow.victor.stats.repository.MetricsRepository.BehaviorMetrics> behaviorMap,
            LocalDate startDate, LocalDate endDate) {

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("experimentId", expId);
        report.put("experimentName", experiment.getName());
        report.put("status", "running");
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("timeRange", Map.of("start", startDate.toString(), "end", endDate.toString()));

        // Per-variant user counts from ClickHouse experiment_metrics
        var variantStats = statsEngine.getMetricsRepository().queryExperimentStats(expId, startDate, endDate);

        // SRM check
        int n = variants.size();
        long[] observedArr = new long[n];
        double[] expectedArr = new double[n];
        Map<String, Long> observedMap = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Bucket v = variants.get(i);
            String key = v.getBucketId() != null ? v.getBucketId() : v.getName();
            var vs = variantStats.get(key);
            observedArr[i] = vs != null ? vs.getTotalUsers() : 0;
            expectedArr[i] = expectedProportions.getOrDefault(key, 1.0 / n);
            observedMap.put(key, observedArr[i]);
        }
        if (Arrays.stream(observedArr).sum() > 0) {
            double srmPValue = com.gateflow.victor.stats.algorithm.SrmTest.chiSquareTest(observedArr, expectedArr);
            report.put("srmCheck", Map.of(
                "passed", srmPValue > 0.01,
                "pValue", srmPValue,
                "observedCounts", observedMap,
                "expectedRatios", expectedProportions
            ));
        }

        // Bucket summaries with behavior metrics
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Bucket v : variants) {
            String key = v.getBucketId() != null ? v.getBucketId() : v.getName();
            var vs = variantStats.get(key);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("variant", key);
            s.put("totalUsers", vs != null ? vs.getTotalUsers() : 0);
            s.put("isControl", key.equals(controlVariant));

            var bm = behaviorMap.get(key);
            if (bm != null) {
                Map<String, Object> behavior = new LinkedHashMap<>();
                behavior.put("exposureUv", bm.exposureUv);
                behavior.put("exposurePv", bm.exposurePv);
                behavior.put("clickUv", bm.clickUv);
                behavior.put("clickPv", bm.clickPv);
                behavior.put("penetrationRate", Math.round(bm.getPenetrationRate() * 10000.0) / 10000.0);
                s.put("behavior", behavior);
            }
            summaries.add(s);
        }
        report.put("variantSummaries", summaries);

        return report;
    }

    private Map<String, Object> buildEmptyReport(String expId) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("experimentId", expId);
        report.put("status", "no_data");
        report.put("generatedAt", LocalDateTime.now().toString());
        report.put("message", "实验数据不足，无法生成报告");
        return report;
    }

    private Map<String, Object> convertReportToMap(ExperimentReport report, Experiment experiment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("experimentId", report.getExpId());
        map.put("experimentName", experiment.getName());
        map.put("status", experiment.getStatus());
        map.put("generatedAt", LocalDateTime.now().toString());
        map.put("timeRange", Map.of(
            "start", report.getStartDate().toString(),
            "end", report.getEndDate().toString()
        ));

        // SRM check
        if (report.getSrmCheck() != null) {
            map.put("srmCheck", Map.of(
                "passed", report.getSrmCheck().isPassed(),
                "chiSquareStat", report.getSrmCheck().getChiSquareStatistic(),
                "pValue", report.getSrmCheck().getPValue(),
                "observedCounts", report.getSrmCheck().getObservedCounts() != null
                    ? report.getSrmCheck().getObservedCounts() : Map.of(),
                "expectedRatios", report.getSrmCheck().getExpectedRatios() != null
                    ? report.getSrmCheck().getExpectedRatios() : Map.of()
            ));
        }

        // Primary metric
        if (report.getPrimaryMetric() != null) {
            map.put("primaryMetrics", List.of(convertTestResult(report.getPrimaryMetric(), "主指标")));
        } else {
            map.put("primaryMetrics", List.of());
        }

        // Secondary metrics
        List<Map<String, Object>> secondaryList = new ArrayList<>();
        if (report.getSecondaryMetrics() != null) {
            for (TestResult r : report.getSecondaryMetrics()) {
                secondaryList.add(convertTestResult(r, r.getTestName()));
            }
        }
        map.put("secondaryMetrics", secondaryList);

        // Guardrail metrics
        List<Map<String, Object>> guardrailList = new ArrayList<>();
        if (report.getGuardrailMetrics() != null) {
            for (SequentialTestResult r : report.getGuardrailMetrics()) {
                Map<String, Object> gm = new LinkedHashMap<>();
                gm.put("metricName", r.getTestName());
                gm.put("lambda", r.getLambda());
                gm.put("status", r.getStatus().name());
                gm.put("message", r.getMessage());
                guardrailList.add(gm);
            }
        }
        map.put("guardrailMetrics", guardrailList);

        // Recommendation
        if (report.getRecommendation() != null) {
            map.put("recommendation", Map.of(
                "action", report.getRecommendation().name(),
                "reasoning", report.getRecommendationReason() != null
                    ? report.getRecommendationReason() : ""
            ));
        }

        // Bucket summaries
        if (report.getVariantSummaries() != null) {
            List<Map<String, Object>> summaries = new ArrayList<>();
            for (ExperimentReport.VariantSummary vs : report.getVariantSummaries().values()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("variant", vs.getVariant());
                s.put("totalUsers", vs.getTotalUsers());
                s.put("totalConversions", vs.getTotalConversions());
                s.put("conversionRate", vs.getConversionRate());
                s.put("avgRevenuePerUser", vs.getAvgRevenuePerUser());
                s.put("isControl", vs.isControl());
                if (vs.getBehaviorMetrics() != null) {
                    var bm = vs.getBehaviorMetrics();
                    Map<String, Object> behavior = new LinkedHashMap<>();
                    behavior.put("exposureUv", bm.exposureUv);
                    behavior.put("exposurePv", bm.exposurePv);
                    behavior.put("clickUv", bm.clickUv);
                    behavior.put("clickPv", bm.clickPv);
                    behavior.put("penetrationRate", bm.getPenetrationRate());
                    s.put("behavior", behavior);
                }
                summaries.add(s);
            }
            map.put("variantSummaries", summaries);
        }

        // Daily trends
        if (report.getDailyTrends() != null) {
            Map<String, List<Map<String, Object>>> trends = new LinkedHashMap<>();
            for (Map.Entry<String, List<ExperimentReport.DailyMetric>> entry : report.getDailyTrends().entrySet()) {
                List<Map<String, Object>> dailyList = new ArrayList<>();
                for (ExperimentReport.DailyMetric dm : entry.getValue()) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("date", dm.getDate().toString());
                    d.put("users", dm.getUsers());
                    d.put("conversions", dm.getConversions());
                    d.put("conversionRate", dm.getConversionRate());
                    d.put("revenue", dm.getRevenue());
                    dailyList.add(d);
                }
                trends.put(entry.getKey(), dailyList);
            }
            map.put("dailyTrend", trends);
        }

        return map;
    }

    /**
     * Parse guardrail_metrics JSON from experiment config.
     * Supports both ["metric1","metric2"] and [{"name":"metric1"},{"name":"metric2"}] formats.
     */
    private List<String> parseGuardrailMetrics(String guardrailMetricsJson) {
        if (guardrailMetricsJson == null || guardrailMetricsJson.isBlank()) {
            return null; // StatsEngine will use default
        }
        try {
            List<?> raw = objectMapper.readValue(guardrailMetricsJson, new TypeReference<List<?>>() {});
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
            log.warn("Failed to parse guardrail_metrics JSON: {}", guardrailMetricsJson, e);
            return null;
        }
    }

    private Map<String, Object> convertTestResult(TestResult result, String metricName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("metricName", metricName);
        m.put("pValue", result.getPValue());
        m.put("isSignificant", result.isSignificant());
        m.put("testMethod", "z-test");
        if (result.getLift() != null) {
            m.put("lift", result.getLift().getValue());
            m.put("liftLowerCI", result.getLift().getConfidenceIntervalLower());
            m.put("liftUpperCI", result.getLift().getConfidenceIntervalUpper());
        }
        return m;
    }
}
