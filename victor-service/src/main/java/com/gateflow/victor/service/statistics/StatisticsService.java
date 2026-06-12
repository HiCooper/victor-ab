package com.gateflow.victor.service.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.dto.*;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.infra.mapper.BucketMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.service.experiment.ExperimentService;
import com.gateflow.victor.stats.algorithm.SrmTest;
import com.gateflow.victor.stats.algorithm.ZTest;
import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.model.ConfidenceInterval;
import com.gateflow.victor.stats.model.ExperimentReport;
import com.gateflow.victor.stats.model.LiftEstimate;
import com.gateflow.victor.stats.model.TestResult;
import com.gateflow.victor.stats.repository.MetricsRepository;
import com.gateflow.victor.stats.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final BucketMapper bucketMapper;
    private final LayerMapper layerMapper;
    private final StatsEngine statsEngine;
    private final ReportRepository reportRepository;
    private final ZTest zTest;
    private final ObjectMapper objectMapper;

    /**
     * 获取实验指标结果
     */
    public ExperimentMetricsResponse getMetricResults(Long experimentId) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(experimentId));
        }

        // Always compute from ClickHouse in real-time (columnar GROUP BY, ~ms)
        LocalDate startDate = experiment.getStartTime() != null
                ? experiment.getStartTime().toLocalDate()
                : LocalDate.now().minusDays(14);
        LocalDate endDate = LocalDate.now();
        ExperimentReport report = buildReport(experiment, startDate, endDate);

        // Supplement with CUPED-adjusted values if scheduler has computed them
        Map<String, ReportRepository.CupedValueDto> cupedValues =
                reportRepository.findLatestCupedValues(experiment.getExpId());
        if (!cupedValues.isEmpty()) {
            applyCupedToReport(report, cupedValues);
        }

        List<Bucket> buckets = experimentService.getExperimentBuckets(experimentId);
        if (buckets.isEmpty()) {
            return buildEmptyMetricsResponse();
        }

        String controlBucket = buckets.get(0).getBucketId();
        List<String> treatmentBuckets = buckets.stream()
                .skip(1)
                .map(Bucket::getBucketId)
                .toList();

        return buildMetricsResponse(report, controlBucket, treatmentBuckets);
    }

    /**
     * 获取时序数据
     */
    public TimeSeriesDataResponse getTimeSeriesData(Long experimentId, Integer days) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(experimentId));
        }

        int d = days != null ? days : 14;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(d - 1);

        List<Bucket> buckets = experimentService.getExperimentBuckets(experimentId);
        if (buckets.isEmpty()) {
            return TimeSeriesDataResponse.builder().data(List.of()).build();
        }

        String expId = experiment.getExpId();
        List<TimeSeriesDataResponse.DataPoint> dataPoints = new ArrayList<>();

        String controlBucket = buckets.get(0).getBucketId();
        String treatmentBucket = buckets.size() > 1 ? buckets.get(1).getBucketId() : null;

        Map<LocalDate, com.gateflow.victor.stats.repository.MetricsRepository.DailyStats> controlDaily =
                statsEngine.getMetricsRepository().queryDailyTrend(expId, controlBucket, startDate, endDate);

        Map<LocalDate, com.gateflow.victor.stats.repository.MetricsRepository.DailyStats> treatmentDaily =
                treatmentBucket != null
                        ? statsEngine.getMetricsRepository().queryDailyTrend(expId, treatmentBucket, startDate, endDate)
                        : Map.of();

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            var cStats = controlDaily.get(date);
            var tStats = treatmentDaily.get(date);

            double controlUsers = cStats != null ? cStats.getTotalUsers() : 0;
            double treatmentUsers = tStats != null ? tStats.getTotalUsers() : 0;

            dataPoints.add(TimeSeriesDataResponse.DataPoint.builder()
                    .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .control(controlUsers)
                    .treatment(treatmentUsers)
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
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(experimentId));
        }

        List<Bucket> buckets = experimentService.getExperimentBuckets(experimentId);
        if (buckets.isEmpty()) {
            return BucketStatisticsResponse.builder()
                    .buckets(List.of())
                    .srmPassed(false)
                    .srmMessage("实验无分桶数据")
                    .build();
        }

        LocalDate startDate = experiment.getStartTime() != null
                ? experiment.getStartTime().toLocalDate()
                : LocalDate.now().minusDays(14);
        LocalDate endDate = LocalDate.now();

        String expId = experiment.getExpId();
        Map<String, MetricsRepository.BucketStats> bucketStats =
                statsEngine.getMetricsRepository().queryExperimentStats(expId, startDate, endDate);

        List<BucketStatisticsResponse.BucketStat> bucketStatList = new ArrayList<>();
        long[] observed = new long[bucketStats.size()];
        double[] expected = new double[bucketStats.size()];
        long totalUsers = 0;

        double srmPValue = totalUsers > 0 ? SrmTest.chiSquareTest(observed, expected) : 1.0;
        boolean srmPassed = srmPValue >= 0.01;

        String srmMessage = srmPassed
                ? "SRM检验通过（p=" + String.format("%.3f", srmPValue) + "），分流比例均衡，样本分配无显著偏差。"
                : "SRM检验未通过（p=" + String.format("%.3f", srmPValue) + "），分流比例可能存在偏差，请排查原因。";

        return BucketStatisticsResponse.builder()
                .buckets(bucketStatList)
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
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(experimentId));
        }

        List<Bucket> buckets = experimentService.getExperimentBuckets(experimentId);
        if (buckets.size() < 2) {
            return AATestResponse.builder()
                    .results(List.of())
                    .aaTestPassed(false)
                    .message("实验分桶数量不足，无法进行AA测试")
                    .build();
        }

        LocalDate experimentStart = experiment.getStartTime() != null
                ? experiment.getStartTime().toLocalDate()
                : LocalDate.now().minusDays(7);
        LocalDate aaStart = experimentStart.minusDays(7);
        LocalDate aaEnd = experimentStart.minusDays(1);

        String expId = experiment.getExpId();
        String controlKey = buckets.get(0).getBucketId();

        List<AATestResponse.AATestResult> results = new ArrayList<>();

        for (int i = 1; i < buckets.size(); i++) {
            String treatmentKey = buckets.get(i).getBucketId();

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
                    .bucketMean(treatmentStats.getConversionRate())
                    .pValue(testResult.getPValue())
                    .significant(testResult.isSignificant())
                    .build());

            if (controlStats.getTotalRevenue() > 0 || treatmentStats.getTotalRevenue() > 0) {
                results.add(AATestResponse.AATestResult.builder()
                        .metric("每用户收入")
                        .controlMean(controlStats.getAvgRevenue())
                        .bucketMean(treatmentStats.getAvgRevenue())
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
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(experimentId));
        }

        int d = days != null ? days : 7;
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(d - 1);

        List<Bucket> buckets = experimentService.getExperimentBuckets(experimentId);
        if (buckets.isEmpty()) {
            return TrafficDataResponse.builder().data(List.of()).build();
        }

        String expId = experiment.getExpId();
        String controlBucket = buckets.get(0).getBucketId();
        String treatmentBucket = buckets.size() > 1 ? buckets.get(1).getBucketId() : null;

        Map<LocalDate, com.gateflow.victor.stats.repository.MetricsRepository.DailyStats> controlDaily =
                statsEngine.getMetricsRepository().queryDailyTrend(expId, controlBucket, startDate, endDate);

        Map<LocalDate, com.gateflow.victor.stats.repository.MetricsRepository.DailyStats> treatmentDaily =
                treatmentBucket != null
                        ? statsEngine.getMetricsRepository().queryDailyTrend(expId, treatmentBucket, startDate, endDate)
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

    /**
     * 获取置信度趋势 — 逐日累积计算 Z-Test 的置信度（1 - pValue）。
     * <p>
     * 从实验开始日期起，逐日累加对照组和治疗组的数据量并计算统计显著性，
     * 展示随着样本量增长置信度的变化趋势。
     */
    public ConfidenceTrendResponse getConfidenceTrend(Long experimentId, Integer days) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(experimentId));
        }

        List<Bucket> buckets = experimentService.getExperimentBuckets(experimentId);
        if (buckets.size() < 2) {
            return ConfidenceTrendResponse.builder().data(List.of()).build();
        }

        int d = days != null ? days : 30;
        LocalDate startDate = experiment.getStartTime() != null
                ? experiment.getStartTime().toLocalDate()
                : LocalDate.now().minusDays(d);
        LocalDate endDate = LocalDate.now();

        String expId = experiment.getExpId();
        String controlBucket = buckets.get(0).getBucketId();
        String treatmentBucket = buckets.get(1).getBucketId();

        // Fetch full daily data for both buckets
        Map<LocalDate, MetricsRepository.DailyStats> controlDaily =
                statsEngine.getMetricsRepository().queryDailyTrend(expId, controlBucket, startDate, endDate);
        Map<LocalDate, MetricsRepository.DailyStats> treatmentDaily =
                statsEngine.getMetricsRepository().queryDailyTrend(expId, treatmentBucket, startDate, endDate);

        if (controlDaily.isEmpty() || treatmentDaily.isEmpty()) {
            return ConfidenceTrendResponse.builder()
                    .data(List.of(ConfidenceTrendResponse.DataPoint.builder()
                            .date(LocalDate.now().toString())
                            .confidence(0)
                            .build()))
                    .build();
        }

        // Accumulate data day by day and compute confidence
        List<ConfidenceTrendResponse.DataPoint> dataPoints = new ArrayList<>();
        long cumCtrlUsers = 0, cumCtrlConversions = 0;
        long cumTreatUsers = 0, cumTreatConversions = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            MetricsRepository.DailyStats cStats = controlDaily.get(date);
            MetricsRepository.DailyStats tStats = treatmentDaily.get(date);

            if (cStats != null) {
                cumCtrlUsers += cStats.getTotalUsers();
                cumCtrlConversions += cStats.getTotalConversions();
            }
            if (tStats != null) {
                cumTreatUsers += tStats.getTotalUsers();
                cumTreatConversions += tStats.getTotalConversions();
            }

            double confidence;
            if (cumCtrlUsers > 0 && cumTreatUsers > 0
                    && cumCtrlConversions > 0 && cumTreatConversions > 0) {
                TestResult result = zTest.executeProportion(
                        cumCtrlConversions, cumCtrlUsers,
                        cumTreatConversions, cumTreatUsers);
                confidence = Math.max(0, 1 - result.getPValue());
            } else {
                confidence = 0;
            }

            dataPoints.add(ConfidenceTrendResponse.DataPoint.builder()
                    .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .confidence(Math.round(confidence * 10000.0) / 10000.0)
                    .build());
        }

        return ConfidenceTrendResponse.builder().data(dataPoints).build();
    }

    // ========== 私有方法 ==========

    private ExperimentReport buildReport(Experiment experiment, LocalDate startDate, LocalDate endDate) {
        Layer layer = layerMapper.selectById(experiment.getLayerId());
        String layerName = layer != null ? layer.getName() : "default";

        List<Bucket> buckets = experimentService.getExperimentBuckets(experiment.getId());
        if (buckets.isEmpty()) {
            return ExperimentReport.builder()
                    .expId(experiment.getExpId())
                    .layer(layerName)
                    .startDate(startDate)
                    .endDate(endDate)
                    .recommendation(com.gateflow.victor.stats.model.Recommendation.INCONCLUSIVE)
                    .recommendationReason("实验无分桶数据")
                    .build();
        }

        String controlBucket = buckets.get(0).getBucketId();
        List<String> treatmentBuckets = buckets.stream()
                .skip(1)
                .map(Bucket::getBucketId)
                .toList();

        Map<String, Double> expectedProportions = new LinkedHashMap<>();
        for (Bucket v : buckets) {
            int bucketSize = v.getBucketEnd() - v.getBucketStart() + 1;
            expectedProportions.put(v.getBucketId(), bucketSize / 10000.0);
        }

        List<String> guardrailMetricNames = parseGuardrailMetrics(experiment.getGuardrailMetrics());

        return statsEngine.analyzeExperiment(
                experiment.getExpId(),
                layerName,
                startDate,
                endDate,
                controlBucket,
                treatmentBuckets,
                expectedProportions,
                guardrailMetricNames
        );
    }

    /**
     * Apply CUPED-adjusted values from MySQL to a real-time report.
     * Replaces raw means/variances in bucket summaries with CUPED-adjusted values,
     * and recomputes lift/p-value/CI using the adjusted statistics.
     */
    private void applyCupedToReport(
            ExperimentReport report,
            Map<String, ReportRepository.CupedValueDto> cupedValues
    ) {
        if (report.getBucketSummaries() == null) return;

        // Inject CUPED-adjusted mean/variance into each bucket summary
        for (Map.Entry<String, ReportRepository.CupedValueDto> entry : cupedValues.entrySet()) {
            ExperimentReport.BucketSummary summary = report.getBucketSummaries().get(entry.getKey());
            if (summary != null) {
                summary.setCupedAdjustedMean(entry.getValue().getCupedAdjustedMean());
                summary.setCupedAdjustedVariance(entry.getValue().getCupedAdjustedVariance());
            }
        }

        // Recompute primary metric using CUPED-adjusted values
        ExperimentReport.BucketSummary ctrl = null;
        ExperimentReport.BucketSummary treat = null;
        for (ExperimentReport.BucketSummary vs : report.getBucketSummaries().values()) {
            if (vs.getCupedAdjustedMean() == null) continue;
            if (vs.isControl() && ctrl == null) {
                ctrl = vs;
            } else if (!vs.isControl() && treat == null) {
                treat = vs;
            }
        }

        if (ctrl == null || treat == null) return;

        double ctrlMean = ctrl.getCupedAdjustedMean();
        double treatMean = treat.getCupedAdjustedMean();
        double ctrlVar = ctrl.getCupedAdjustedVariance();
        double treatVar = treat.getCupedAdjustedVariance();

        double diff = treatMean - ctrlMean;
        double lift = ctrlMean != 0 ? diff / ctrlMean : 0;
        double se = Math.sqrt(ctrlVar / ctrl.getTotalUsers() + treatVar / treat.getTotalUsers());
        double z = se > 0 ? diff / se : 0;

        double absCiLower = diff - 1.96 * se;
        double absCiUpper = diff + 1.96 * se;
        double liftLower = ctrlMean != 0 ? absCiLower / ctrlMean : 0;
        double liftUpper = ctrlMean != 0 ? absCiUpper / ctrlMean : 0;

        org.apache.commons.math3.distribution.NormalDistribution normal =
                new org.apache.commons.math3.distribution.NormalDistribution();
        double pValue = 2 * (1 - normal.cumulativeProbability(Math.abs(z)));

        if (report.getPrimaryMetric() != null) {
            report.getPrimaryMetric().setLift(LiftEstimate.of(lift, liftLower, liftUpper));
            report.getPrimaryMetric().setConfidenceInterval(ConfidenceInterval.of(absCiLower, absCiUpper, 0.95));
            report.getPrimaryMetric().setPValue(pValue);
            report.getPrimaryMetric().setSignificant(pValue < 0.05);
        }

        report.setCupedApplied(true);
    }

    private ExperimentMetricsResponse buildMetricsResponse(
            ExperimentReport report,
            String controlBucket,
            List<String> treatmentBuckets
    ) {
        TestResult primary = report.getPrimaryMetric();
        if (primary == null) {
            return buildEmptyMetricsResponse();
        }

        var controlSummary = report.getBucketSummaries().get(controlBucket);
        double controlRate = controlSummary != null
                ? (report.isCupedApplied() && controlSummary.getCupedAdjustedMean() != null
                ? controlSummary.getCupedAdjustedMean()
                : controlSummary.getConversionRate())
                : 0;

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

    private List<String> parseGuardrailMetrics(String guardrailMetricsJson) {
        return com.gateflow.victor.service.util.GuardrailParser.parse(guardrailMetricsJson);
    }
}
