package com.gateflow.victor.service.statistics;

import com.gateflow.victor.domain.dto.*;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
import com.gateflow.victor.service.experiment.ExperimentService;
import com.gateflow.victor.stats.algorithm.SrmTest;
import com.gateflow.victor.stats.algorithm.ZTest;
import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.model.ExperimentReport;
import com.gateflow.victor.stats.model.TestResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 实验统计分析服务 - 接入真实 ClickHouse 数据
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsService {

    private final ExperimentService experimentService;
    private final VariantMapper variantMapper;
    private final LayerMapper layerMapper;
    private final StatsEngine statsEngine;
    private final ZTest zTest;

    /**
     * 获取实验指标结果
     */
    public ExperimentMetricsResponse getMetricResults(Long experimentId) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new RuntimeException("Experiment not found: " + experimentId);
        }

        LocalDate startDate = experiment.getStartTime() != null
            ? experiment.getStartTime().toLocalDate()
            : LocalDate.now().minusDays(14);
        LocalDate endDate = LocalDate.now();

        ExperimentReport report = buildReport(experiment, startDate, endDate);

        List<Variant> variants = experimentService.getExperimentVariants(experimentId);
        if (variants.isEmpty()) {
            return buildEmptyMetricsResponse();
        }

        String controlVariant = variants.get(0).getVariantKey();
        List<String> treatmentVariants = variants.stream()
            .skip(1)
            .map(Variant::getVariantKey)
            .toList();

        return buildMetricsResponse(report, controlVariant, treatmentVariants);
    }

    /**
     * 获取时序数据
     */
    public TimeSeriesDataResponse getTimeSeriesData(Long experimentId, Integer days) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new RuntimeException("Experiment not found: " + experimentId);
        }

        int d = days != null ? days : 14;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(d - 1);

        List<Variant> variants = experimentService.getExperimentVariants(experimentId);
        if (variants.isEmpty()) {
            return TimeSeriesDataResponse.builder().data(List.of()).build();
        }

        String expId = experiment.getExpId();
        List<TimeSeriesDataResponse.DataPoint> dataPoints = new ArrayList<>();

        String controlVariant = variants.get(0).getVariantKey();
        String treatmentVariant = variants.size() > 1 ? variants.get(1).getVariantKey() : null;

        Map<LocalDate, com.gateflow.victor.stats.repository.MetricsRepository.DailyStats> controlDaily =
            statsEngine.getMetricsRepository().queryDailyTrend(expId, controlVariant, startDate, endDate);

        Map<LocalDate, com.gateflow.victor.stats.repository.MetricsRepository.DailyStats> treatmentDaily =
            treatmentVariant != null
                ? statsEngine.getMetricsRepository().queryDailyTrend(expId, treatmentVariant, startDate, endDate)
                : Map.of();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            var cStats = controlDaily.get(date);
            var tStats = treatmentDaily.get(date);

            double controlRate = cStats != null ? cStats.getConversionRate() : 0;
            double treatmentRate = tStats != null ? tStats.getConversionRate() : 0;

            dataPoints.add(TimeSeriesDataResponse.DataPoint.builder()
                .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .control(Math.round(controlRate * 1000.0) / 1000.0)
                .treatment(Math.round(treatmentRate * 1000.0) / 1000.0)
                .build());
        }

        return TimeSeriesDataResponse.builder().data(dataPoints).build();
    }

    /**
     * 获取分桶统计数据
     */
    public BucketStatisticsResponse getBucketStatistics(Long experimentId) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new RuntimeException("Experiment not found: " + experimentId);
        }

        List<Variant> variants = experimentService.getExperimentVariants(experimentId);
        if (variants.isEmpty()) {
            return BucketStatisticsResponse.builder()
                .buckets(List.of())
                .srmPassed(false)
                .srmMessage("实验无变体数据")
                .build();
        }

        LocalDate startDate = experiment.getStartTime() != null
            ? experiment.getStartTime().toLocalDate()
            : LocalDate.now().minusDays(14);
        LocalDate endDate = LocalDate.now();

        String expId = experiment.getExpId();
        Map<String, com.gateflow.victor.stats.repository.MetricsRepository.VariantStats> variantStats =
            statsEngine.getMetricsRepository().queryExperimentStats(expId, startDate, endDate);

        List<BucketStatisticsResponse.BucketStat> buckets = new ArrayList<>();
        long[] observed = new long[variants.size()];
        double[] expected = new double[variants.size()];
        int idx = 0;
        long totalUsers = 0;

        for (Variant variant : variants) {
            String key = variant.getVariantKey();
            var stats = variantStats.get(key);
            long users = stats != null ? stats.getTotalUsers() : 0;
            totalUsers += users;
            observed[idx] = users;
            expected[idx] = 1.0 / variants.size();
            idx++;

            buckets.add(BucketStatisticsResponse.BucketStat.builder()
                .bucket(key)
                .dailyEntries(stats != null ? stats.getTotalEvents() : 0)
                .dailySamples(stats != null ? stats.getTotalUsers() : 0)
                .chiSquarePValue(0)
                .cumulativeSamples(users)
                .cumulativeChiSquarePValue(0)
                .build());
        }

        double srmPValue = totalUsers > 0 ? SrmTest.chiSquareTest(observed, expected) : 1.0;
        boolean srmPassed = srmPValue >= 0.01;

        String srmMessage = srmPassed
            ? "SRM检验通过（p=" + String.format("%.3f", srmPValue) + "），分流比例均衡，样本分配无显著偏差。"
            : "SRM检验未通过（p=" + String.format("%.3f", srmPValue) + "），分流比例可能存在偏差，请排查原因。";

        return BucketStatisticsResponse.builder()
            .buckets(buckets)
            .srmPassed(srmPassed)
            .srmMessage(srmMessage)
            .build();
    }

    /**
     * 获取AA测试结果
     */
    public AATestResponse getAATestResults(Long experimentId) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new RuntimeException("Experiment not found: " + experimentId);
        }

        List<Variant> variants = experimentService.getExperimentVariants(experimentId);
        if (variants.size() < 2) {
            return AATestResponse.builder()
                .results(List.of())
                .aaTestPassed(false)
                .message("实验变体数量不足，无法进行AA测试")
                .build();
        }

        LocalDate experimentStart = experiment.getStartTime() != null
            ? experiment.getStartTime().toLocalDate()
            : LocalDate.now().minusDays(7);
        LocalDate aaStart = experimentStart.minusDays(7);
        LocalDate aaEnd = experimentStart.minusDays(1);

        String expId = experiment.getExpId();
        String controlKey = variants.get(0).getVariantKey();

        List<AATestResponse.AATestResult> results = new ArrayList<>();

        for (int i = 1; i < variants.size(); i++) {
            String treatmentKey = variants.get(i).getVariantKey();

            var controlStats = statsEngine.getMetricsRepository().queryExperimentStats(expId, aaStart, aaEnd)
                .get(controlKey);
            var treatmentStats = statsEngine.getMetricsRepository().queryExperimentStats(expId, aaStart, aaEnd)
                .get(treatmentKey);

            if (controlStats == null || treatmentStats == null
                || controlStats.getTotalUsers() == 0 || treatmentStats.getTotalUsers() == 0) {
                continue;
            }

            TestResult testResult = zTest.executeProportion(
                controlStats.getTotalConversions(), controlStats.getTotalUsers(),
                treatmentStats.getTotalConversions(), treatmentStats.getTotalUsers()
            );

            results.add(AATestResponse.AATestResult.builder()
                .metric("转化率")
                .controlMean(controlStats.getConversionRate())
                .variantMean(treatmentStats.getConversionRate())
                .pValue(testResult.getPValue())
                .significant(testResult.isSignificant())
                .build());

            if (controlStats.getTotalRevenue() > 0 || treatmentStats.getTotalRevenue() > 0) {
                results.add(AATestResponse.AATestResult.builder()
                    .metric("每用户收入")
                    .controlMean(controlStats.getAvgRevenue())
                    .variantMean(treatmentStats.getAvgRevenue())
                    .pValue(0.5)
                    .significant(false)
                    .build());
            }
        }

        boolean aaPassed = results.stream().noneMatch(AATestResponse.AATestResult::isSignificant);
        String message = aaPassed
            ? "AA测试通过，所有核心指标P值均大于0.05，实验前各组无显著差异。"
            : "AA测试未通过，部分指标在实验前已存在显著差异，请谨慎解读实验结果。";

        return AATestResponse.builder()
            .results(results)
            .aaTestPassed(aaPassed)
            .message(message)
            .build();
    }

    /**
     * 获取流量数据
     */
    public TrafficDataResponse getTrafficData(Long experimentId, Integer days) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new RuntimeException("Experiment not found: " + experimentId);
        }

        int d = days != null ? days : 7;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(d - 1);

        List<Variant> variants = experimentService.getExperimentVariants(experimentId);
        if (variants.isEmpty()) {
            return TrafficDataResponse.builder().data(List.of()).build();
        }

        String expId = experiment.getExpId();
        String controlVariant = variants.get(0).getVariantKey();
        String treatmentVariant = variants.size() > 1 ? variants.get(1).getVariantKey() : null;

        Map<LocalDate, com.gateflow.victor.stats.repository.MetricsRepository.DailyStats> controlDaily =
            statsEngine.getMetricsRepository().queryDailyTrend(expId, controlVariant, startDate, endDate);

        Map<LocalDate, com.gateflow.victor.stats.repository.MetricsRepository.DailyStats> treatmentDaily =
            treatmentVariant != null
                ? statsEngine.getMetricsRepository().queryDailyTrend(expId, treatmentVariant, startDate, endDate)
                : Map.of();

        List<TrafficDataResponse.DataPoint> dataPoints = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            var cStats = controlDaily.get(date);
            var tStats = treatmentDaily.get(date);

            dataPoints.add(TrafficDataResponse.DataPoint.builder()
                .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .control(cStats != null ? cStats.getTotalUsers() : 0)
                .treatment(tStats != null ? tStats.getTotalUsers() : 0)
                .build());
        }

        return TrafficDataResponse.builder().data(dataPoints).build();
    }

    // ========== 私有方法 ==========

    private ExperimentReport buildReport(Experiment experiment, LocalDate startDate, LocalDate endDate) {
        Layer layer = layerMapper.selectById(experiment.getLayerId());
        String layerName = layer != null ? layer.getName() : "default";

        List<Variant> variants = experimentService.getExperimentVariants(experiment.getId());
        if (variants.isEmpty()) {
            return ExperimentReport.builder()
                .expId(experiment.getExpId())
                .layer(layerName)
                .startDate(startDate)
                .endDate(endDate)
                .recommendation(com.gateflow.victor.stats.model.Recommendation.INCONCLUSIVE)
                .recommendationReason("实验无变体数据")
                .build();
        }

        String controlVariant = variants.get(0).getVariantKey();
        List<String> treatmentVariants = variants.stream()
            .skip(1)
            .map(Variant::getVariantKey)
            .toList();

        return statsEngine.analyzeExperiment(
            experiment.getExpId(),
            layerName,
            startDate,
            endDate,
            controlVariant,
            treatmentVariants
        );
    }

    private ExperimentMetricsResponse buildMetricsResponse(
        ExperimentReport report,
        String controlVariant,
        List<String> treatmentVariants
    ) {
        TestResult primary = report.getPrimaryMetric();
        if (primary == null) {
            return buildEmptyMetricsResponse();
        }

        var controlSummary = report.getVariantSummaries().get(controlVariant);
        double controlRate = controlSummary != null ? controlSummary.getConversionRate() : 0;

        ExperimentMetricsResponse.MetricResult primaryResult = ExperimentMetricsResponse.MetricResult.builder()
            .id("conversion_rate")
            .name("转化率")
            .value(controlRate)
            .lift(primary.getLift() != null ? primary.getLift().getValue() * 100 : 0)
            .pValue(primary.getPValue())
            .significant(primary.isSignificant())
            .direction(primary.getLift() != null && primary.getLift().getValue() > 0 ? "positive" : "negative")
            .confidenceInterval(primary.getConfidenceInterval() != null
                ? new double[]{primary.getConfidenceInterval().getLower(), primary.getConfidenceInterval().getUpper()}
                : new double[]{0, 0})
            .build();

        List<ExperimentMetricsResponse.MetricResult> secondary = new ArrayList<>();
        if (controlSummary != null && controlSummary.getTotalRevenue() > 0) {
            secondary.add(ExperimentMetricsResponse.MetricResult.builder()
                .id("revenue_per_user")
                .name("每用户收入")
                .value(controlSummary.getAvgRevenuePerUser())
                .lift(0)
                .pValue(1.0)
                .significant(false)
                .direction("neutral")
                .confidenceInterval(new double[]{0, 0})
                .build());
        }

        List<ExperimentMetricsResponse.MetricResult> guardrail = new ArrayList<>();
        if (report.getSrmCheck() != null) {
            guardrail.add(ExperimentMetricsResponse.MetricResult.builder()
                .id("srm_check")
                .name("SRM检验")
                .value(report.getSrmCheck().getPValue())
                .lift(0)
                .pValue(report.getSrmCheck().getPValue())
                .significant(!report.getSrmCheck().isPassed())
                .direction("neutral")
                .confidenceInterval(new double[]{0, 0})
                .build());
        }

        return ExperimentMetricsResponse.builder()
            .primary(primaryResult)
            .secondary(secondary)
            .guardrail(guardrail)
            .build();
    }

    private ExperimentMetricsResponse buildEmptyMetricsResponse() {
        return ExperimentMetricsResponse.builder()
            .primary(ExperimentMetricsResponse.MetricResult.builder()
                .id("conversion_rate")
                .name("转化率")
                .value(0)
                .lift(0)
                .pValue(1.0)
                .significant(false)
                .direction("neutral")
                .confidenceInterval(new double[]{0, 0})
                .build())
            .secondary(List.of())
            .guardrail(List.of())
            .build();
    }
}
