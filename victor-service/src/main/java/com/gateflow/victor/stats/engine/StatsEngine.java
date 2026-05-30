package com.gateflow.victor.stats.engine;

import com.gateflow.victor.stats.algorithm.*;
import com.gateflow.victor.stats.model.*;
import com.gateflow.victor.stats.repository.MetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * StatsEngine - 统计分析引擎
 * 整合所有统计算法，提供端到端的实验分析能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsEngine {

    private final ZTest zTest;
    private final CUPED cuped;
    private final BHCorrection bhCorrection;
    private final mSPRT msprt;
    private final MetricsRepository metricsRepository;

    public double runSRM(long[] observed, double[] expected) {
        return SrmTest.chiSquareTest(observed, expected);
    }

    public TestResult runZTest(long cSuccess, long cTotal, long tSuccess, long tTotal) {
        return zTest.executeProportion(cSuccess, cTotal, tSuccess, tTotal);
    }

    public SampleStatistics runCUPED(List<Double> y, List<Double> x, double meanX) {
        return cuped.adjust(y, x, meanX);
    }

    public List<TestResult> runBHCorrection(List<TestResult> results) {
        return bhCorrection.correct(results);
    }

    public SequentialTestResult runmSPRT(SampleStatistics c, SampleStatistics t, int obs) {
        return msprt.execute(c, t, obs);
    }

    public MetricsRepository getMetricsRepository() {
        return metricsRepository;
    }

    /**
     * 分析实验 - 端到端实验报告生成
     *
     * @param expId                实验ID
     * @param layer                层名称
     * @param startDate            分析开始日期
     * @param endDate              分析结束日期
     * @param controlBucketName    对照组分桶名称
     * @param treatmentBuckets     治疗组分桶名称列表
     * @param expectedProportions  期望分流比例
     * @param guardrailMetricNames 护栏指标名称列表（从实验配置读取，null 则默认使用 avgRevenue）
     * @return 完整的实验报告
     */
    public ExperimentReport analyzeExperiment(
            String expId,
            String layer,
            LocalDate startDate,
            LocalDate endDate,
            String controlBucketName,
            List<String> treatmentBuckets,
            Map<String, Double> expectedProportions,
            List<String> guardrailMetricNames
    ) {
        log.info("Starting experiment analysis: expId={}, layer={}, period={} to {}",
                expId, layer, startDate, endDate);

        long startTime = System.currentTimeMillis();

        // Step 1: 查询所有分桶的统计数据
        Map<String, MetricsRepository.BucketStats> bucketStats =
                metricsRepository.queryExperimentStats(expId, startDate, endDate);

        if (bucketStats.isEmpty()) {
            return buildEmptyReport(expId, layer, startDate, endDate);
        }

        // Step 2: SRM 检验 - 验证分流比例
        ExperimentReport.SrmCheckResult srmResult = runSRMCheck(bucketStats, controlBucketName, treatmentBuckets, expectedProportions);

        // Step 3: 获取对照组统计
        MetricsRepository.BucketStats controlStats = bucketStats.get(controlBucketName);
        if (controlStats == null) {
            return buildEmptyReport(expId, layer, startDate, endDate);
        }

        // Step 3.5: CUPED 方差缩减（使用实验前数据）
        Map<String, SampleStatistics> cupedAdjusted = applyCUPED(
                expId, controlBucketName, treatmentBuckets, startDate, endDate);

        // Step 4: 主指标检验 - Z-Test（CUPED 增强）
        TestResult primaryResult = runPrimaryMetricTest(controlStats, bucketStats, treatmentBuckets, cupedAdjusted);

        // Step 5: 辅助指标检验 - BH 校正
        List<TestResult> secondaryResults = runSecondaryMetricsTest(controlStats, bucketStats, treatmentBuckets);

        // Step 6: 护栏指标序贯检验 - mSPRT
        List<SequentialTestResult> guardrailResults = runGuardrailTests(controlStats, bucketStats, guardrailMetricNames);

        // Step 7: 生成决策建议
        Recommendation recommendation = generateRecommendation(
                srmResult, primaryResult, secondaryResults, guardrailResults
        );

        // Step 8: 构建分桶摘要
        Map<String, ExperimentReport.BucketSummary> summaries = buildBucketSummaries(bucketStats, controlBucketName);

        // Step 9: 每日趋势数据
        Map<String, List<ExperimentReport.DailyMetric>> dailyTrends = buildDailyTrends(
                expId, controlBucketName, treatmentBuckets, startDate, endDate
        );

        long endTime = System.currentTimeMillis();

        return ExperimentReport.builder()
                .expId(expId)
                .layer(layer)
                .startDate(startDate)
                .endDate(endDate)
                .srmCheck(srmResult)
                .primaryMetric(primaryResult)
                .secondaryMetrics(secondaryResults)
                .guardrailMetrics(guardrailResults)
                .bucketSummaries(summaries)
                .dailyTrends(dailyTrends)
                .recommendation(recommendation)
                .recommendationReason(buildRecommendationReason(recommendation, srmResult, primaryResult, guardrailResults))
                .generatedAt(endTime - startTime)
                .build();
    }

    /**
     * SRM 检验
     */
    private ExperimentReport.SrmCheckResult runSRMCheck(
            Map<String, MetricsRepository.BucketStats> stats,
            String controlBucket,
            List<String> treatmentBuckets,
            Map<String, Double> expectedProportions
    ) {
        List<String> allBuckets = new ArrayList<>();
        allBuckets.add(controlBucket);
        allBuckets.addAll(treatmentBuckets);

        long[] observed = new long[allBuckets.size()];
        double[] expected = new double[allBuckets.size()];
        Map<String, Long> observedCounts = new HashMap<>();

        long totalUsers = 0;
        for (int i = 0; i < allBuckets.size(); i++) {
            MetricsRepository.BucketStats bucketStats = stats.get(allBuckets.get(i));
            long users = bucketStats != null ? bucketStats.getTotalUsers() : 0;
            observed[i] = users;
            observedCounts.put(allBuckets.get(i), users);
            totalUsers += users;
        }

        // Use actual bucket proportions if available, fall back to equal split
        if (expectedProportions != null && !expectedProportions.isEmpty()) {
            for (int i = 0; i < allBuckets.size(); i++) {
                expected[i] = expectedProportions.getOrDefault(allBuckets.get(i), 1.0 / expected.length);
            }
        } else {
            for (int i = 0; i < expected.length; i++) {
                expected[i] = 1.0 / expected.length;
            }
        }

        SrmTest.ChiSquareResult srmResult = SrmTest.chiSquareTestFull(observed, expected);
        boolean passed = srmResult.pValue() >= 0.01; // SRM 阈值 1%

        Map<String, Double> expectedRatios = new HashMap<>();
        for (int i = 0; i < allBuckets.size(); i++) {
            expectedRatios.put(allBuckets.get(i), expected[i]);
        }

        return ExperimentReport.SrmCheckResult.builder()
                .passed(passed)
                .pValue(srmResult.pValue())
                .chiSquareStatistic(srmResult.chiSquare())
                .observedCounts(observedCounts)
                .expectedRatios(expectedRatios)
                .message(passed ? "SRM检验通过，分流比例正常" : "SRM检验失败，分流比例存在异常")
                .build();
    }

    /**
     * CUPED 方差缩减 — 使用实验前数据降低方差，提高检验灵敏度。
     * 返回 bucket → adjusted SampleStatistics 映射，失败时返回 null。
     */
    private Map<String, SampleStatistics> applyCUPED(
            String expId,
            String controlBucketName,
            List<String> treatmentBuckets,
            LocalDate expStart,
            LocalDate expEnd
    ) {
        long expDays = expStart.until(expEnd).getDays();
        if (expDays < 1) expDays = 7;
        LocalDate preEnd = expStart.minusDays(1);
        LocalDate preStart = preEnd.minusDays(expDays);

        List<String> allBuckets = new ArrayList<>();
        allBuckets.add(controlBucketName);
        allBuckets.addAll(treatmentBuckets);

        // Collect all user-level data and compute overall pre-experiment mean
        List<Double> allPreValues = new ArrayList<>();
        Map<String, List<MetricsRepository.UserMetric>> userDataByBucket = new LinkedHashMap<>();

        for (String bucket : allBuckets) {
            List<MetricsRepository.UserMetric> userData =
                    metricsRepository.queryUserLevelData(expId, bucket, expStart, expEnd, preStart, preEnd);
            if (userData.isEmpty()) {
                log.debug("CUPED: no user-level data for bucket={}, falling back to aggregate", bucket);
                return null;
            }
            userDataByBucket.put(bucket, userData);
            userData.forEach(m -> allPreValues.add(m.getPreExperimentValue()));
        }

        if (allPreValues.isEmpty()) return null;

        double overallMeanX = allPreValues.stream().mapToDouble(d -> d).average().orElse(0);
        log.debug("CUPED: overall pre-experiment mean={}, n={}", overallMeanX, allPreValues.size());

        Map<String, SampleStatistics> adjusted = new LinkedHashMap<>();
        for (String bucket : allBuckets) {
            List<MetricsRepository.UserMetric> userData = userDataByBucket.get(bucket);
            List<Double> y = userData.stream().map(m -> m.getExperimentValue()).toList();
            List<Double> x = userData.stream().map(m -> m.getPreExperimentValue()).toList();

            SampleStatistics cupedStats = cuped.adjust(y, x, overallMeanX);
            adjusted.put(bucket, cupedStats);
        }

        return adjusted;
    }

    /**
     * 主指标检验 — 优先使用 CUPED 调整后的统计量
     */
    private TestResult runPrimaryMetricTest(
            MetricsRepository.BucketStats controlStats,
            Map<String, MetricsRepository.BucketStats> bucketStats,
            List<String> treatmentBuckets,
            Map<String, SampleStatistics> cupedAdjusted
    ) {
        if (treatmentBuckets.isEmpty()) {
            return null;
        }

        String treatmentBucket = treatmentBuckets.get(0);
        MetricsRepository.BucketStats treatmentStats = bucketStats.get(treatmentBucket);

        if (treatmentStats == null) {
            return null;
        }

        // Use CUPED-adjusted statistics when available for higher precision
        if (cupedAdjusted != null && cupedAdjusted.containsKey("control")
                && cupedAdjusted.containsKey(treatmentBucket)) {
            SampleStatistics cCuped = cupedAdjusted.get("control");
            SampleStatistics tCuped = cupedAdjusted.get(treatmentBucket);
            log.debug("Using CUPED-adjusted stats: cMean={}, tMean={}, cVar={}, tVar={}",
                    cCuped.getMean(), tCuped.getMean(), cCuped.getVariance(), tCuped.getVariance());
            return zTest.executeWithStats(cCuped, tCuped);
        }

        // Fall back to aggregate proportion test
        long cSuccess = controlStats.getTotalConversions();
        long cTotal = controlStats.getTotalUsers();
        long tSuccess = treatmentStats.getTotalConversions();
        long tTotal = treatmentStats.getTotalUsers();

        return zTest.executeProportion(cSuccess, cTotal, tSuccess, tTotal);
    }

    /**
     * 辅助指标检验（多治疗组）
     */
    private List<TestResult> runSecondaryMetricsTest(
            MetricsRepository.BucketStats controlStats,
            Map<String, MetricsRepository.BucketStats> bucketStats,
            List<String> treatmentBuckets
    ) {
        List<TestResult> results = new ArrayList<>();

        for (String treatmentBucket : treatmentBuckets) {
            MetricsRepository.BucketStats treatmentStats = bucketStats.get(treatmentBucket);
            if (treatmentStats == null) continue;

            TestResult result = zTest.executeProportion(
                    controlStats.getTotalConversions(),
                    controlStats.getTotalUsers(),
                    treatmentStats.getTotalConversions(),
                    treatmentStats.getTotalUsers()
            );
            result.setTestName("secondary_" + treatmentBucket);
            results.add(result);
        }

        // BH 校正
        return bhCorrection.correct(results);
    }

    /**
     * 护栏指标检验
     *
     * @param guardrailMetricNames 护栏指标名称列表（从实验 guardrail_metrics JSON 配置读取）。
     *                             支持: "avgRevenue", "conversionRate"。
     *                             为 null 或空时默认使用 avgRevenue。
     */
    private List<SequentialTestResult> runGuardrailTests(
            MetricsRepository.BucketStats controlStats,
            Map<String, MetricsRepository.BucketStats> bucketStats,
            List<String> guardrailMetricNames
    ) {
        List<String> metrics = (guardrailMetricNames != null && !guardrailMetricNames.isEmpty())
                ? guardrailMetricNames
                : List.of("avgRevenue");

        List<SequentialTestResult> results = new ArrayList<>();

        for (String metricName : metrics) {
            for (Map.Entry<String, MetricsRepository.BucketStats> entry : bucketStats.entrySet()) {
                if (entry.getKey().equals("control")) continue;

                MetricsRepository.BucketStats treatmentStats = entry.getValue();

                double ctrlMean, treatMean, ctrlVar, treatVar;

                switch (metricName) {
                    case "conversionRate":
                        ctrlMean = controlStats.getConversionRate();
                        treatMean = treatmentStats.getConversionRate();
                        // Binomial variance p*(1-p)
                        ctrlVar = Math.max(ctrlMean * (1 - ctrlMean), 0.0001);
                        treatVar = Math.max(treatMean * (1 - treatMean), 0.0001);
                        break;
                    case "avgRevenue":
                    default:
                        ctrlMean = controlStats.getAvgRevenue();
                        treatMean = treatmentStats.getAvgRevenue();
                        // Revenue variance: use squared mean as rough proxy for continuous data.
                        // TODO: compute proper per-user revenue variance from ClickHouse.
                        ctrlVar = Math.max(ctrlMean * ctrlMean, 0.01);
                        treatVar = Math.max(treatMean * treatMean, 0.01);
                        break;
                }

                SampleStatistics control = SampleStatistics.builder()
                        .n(controlStats.getTotalUsers())
                        .mean(ctrlMean)
                        .variance(ctrlVar)
                        .build();

                SampleStatistics treatment = SampleStatistics.builder()
                        .n(treatmentStats.getTotalUsers())
                        .mean(treatMean)
                        .variance(treatVar)
                        .build();

                SequentialTestResult result = msprt.execute(control, treatment, (int) treatmentStats.getTotalUsers());
                result.setTestName("guardrail_" + metricName + "_" + entry.getKey());
                results.add(result);
            }
        }

        return results;
    }

    /**
     * 生成决策建议
     */
    private Recommendation generateRecommendation(
            ExperimentReport.SrmCheckResult srmResult,
            TestResult primaryResult,
            List<TestResult> secondaryResults,
            List<SequentialTestResult> guardrailResults
    ) {
        // 规则1: SRM 失败 → 不上线
        if (!srmResult.isPassed()) {
            return Recommendation.DO_NOT_LAUNCH;
        }

        // 规则2: 护栏指标恶化 → 不上线
        for (SequentialTestResult guardrail : guardrailResults) {
            if (guardrail.getStatus() == SequentialStatus.STOP_NEGATIVE) {
                return Recommendation.DO_NOT_LAUNCH;
            }
        }

        // 规则3: 主指标显著正向 → 上线
        if (primaryResult != null && primaryResult.isSignificant() && primaryResult.getLift() != null) {
            if (primaryResult.getLift().getValue() > 0) {
                return Recommendation.LAUNCH;
            }
        }

        // 规则4: 样本量不足 → 继续实验
        if (primaryResult == null) {
            return Recommendation.CONTINUE_EXPERIMENT;
        }

        // 默认: 继续实验
        return Recommendation.CONTINUE_EXPERIMENT;
    }

    /**
     * 构建决策原因
     */
    private String buildRecommendationReason(
            Recommendation recommendation,
            ExperimentReport.SrmCheckResult srmResult,
            TestResult primaryResult,
            List<SequentialTestResult> guardrailResults
    ) {
        switch (recommendation) {
            case LAUNCH:
                return "主指标统计显著且方向正向，建议全量上线";
            case DO_NOT_LAUNCH:
                if (!srmResult.isPassed()) {
                    return "SRM检验失败，分流比例异常，不建议上线";
                }
                for (SequentialTestResult guardrail : guardrailResults) {
                    if (guardrail.getStatus() == SequentialStatus.STOP_NEGATIVE) {
                        return "护栏指标" + guardrail.getTestName() + "显著恶化，建议停止实验";
                    }
                }
                return "统计检验不支持上线";
            case CONTINUE_EXPERIMENT:
                return "样本量或显著性不足，建议继续收集数据";
            default:
                return "数据不足，无法得出结论";
        }
    }

    /**
     * 构建分桶摘要
     */
    private Map<String, ExperimentReport.BucketSummary> buildBucketSummaries(
            Map<String, MetricsRepository.BucketStats> stats,
            String controlBucket
    ) {
        Map<String, ExperimentReport.BucketSummary> summaries = new HashMap<>();

        for (Map.Entry<String, MetricsRepository.BucketStats> entry : stats.entrySet()) {
            MetricsRepository.BucketStats bucketStats = entry.getValue();

            summaries.put(entry.getKey(), ExperimentReport.BucketSummary.builder()
                    .bucket(entry.getKey())
                    .totalUsers(bucketStats.getTotalUsers())
                    .totalConversions(bucketStats.getTotalConversions())
                    .conversionRate(bucketStats.getConversionRate())
                    .totalRevenue(bucketStats.getTotalRevenue())
                    .avgRevenuePerUser(bucketStats.getAvgRevenue())
                    .isControl(entry.getKey().equals(controlBucket))
                    .build());
        }

        return summaries;
    }

    /**
     * 构建每日趋势
     */
    private Map<String, List<ExperimentReport.DailyMetric>> buildDailyTrends(
            String expId,
            String controlBucket,
            List<String> treatmentBuckets,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<String, List<ExperimentReport.DailyMetric>> trends = new HashMap<>();

        // 查询各分桶每日趋势
        List<String> allBuckets = new ArrayList<>();
        allBuckets.add(controlBucket);
        allBuckets.addAll(treatmentBuckets);

        for (String bucket : allBuckets) {
            Map<LocalDate, MetricsRepository.DailyStats> dailyStats =
                    metricsRepository.queryDailyTrend(expId, bucket, startDate, endDate);

            List<ExperimentReport.DailyMetric> dailyMetrics = new ArrayList<>();
            for (Map.Entry<LocalDate, MetricsRepository.DailyStats> entry : dailyStats.entrySet()) {
                MetricsRepository.DailyStats stats = entry.getValue();
                dailyMetrics.add(ExperimentReport.DailyMetric.builder()
                        .date(entry.getKey())
                        .users(stats.getTotalUsers())
                        .conversions(stats.getTotalConversions())
                        .conversionRate(stats.getConversionRate())
                        .revenue(stats.getTotalRevenue())
                        .build());
            }

            if (!dailyMetrics.isEmpty()) {
                trends.put(bucket, dailyMetrics);
            }
        }

        return trends;
    }

    /**
     * 构建空报告
     */
    private ExperimentReport buildEmptyReport(
            String expId,
            String layer,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return ExperimentReport.builder()
                .expId(expId)
                .layer(layer)
                .startDate(startDate)
                .endDate(endDate)
                .srmCheck(ExperimentReport.SrmCheckResult.builder()
                        .passed(false)
                        .message("无数据")
                        .build())
                .recommendation(Recommendation.INCONCLUSIVE)
                .recommendationReason("实验数据不足，无法生成报告")
                .generatedAt(0)
                .build();
    }
}