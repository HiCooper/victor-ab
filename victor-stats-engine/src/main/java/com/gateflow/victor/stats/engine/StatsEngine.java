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
     * @param expId 实验ID
     * @param layer 层名称
     * @param startDate 分析开始日期
     * @param endDate 分析结束日期
     * @param controlVariantName 对照组变体名称
     * @param treatmentVariants 治疗组变体名称列表
     * @return 完整的实验报告
     */
    public ExperimentReport analyzeExperiment(
            String expId,
            String layer,
            LocalDate startDate,
            LocalDate endDate,
            String controlVariantName,
            List<String> treatmentVariants,
            Map<String, Double> expectedProportions
    ) {
        log.info("Starting experiment analysis: expId={}, layer={}, period={} to {}", 
            expId, layer, startDate, endDate);
        
        long startTime = System.currentTimeMillis();
        
        // Step 1: 查询所有变体的统计数据
        Map<String, MetricsRepository.VariantStats> variantStats = 
            metricsRepository.queryExperimentStats(expId, startDate, endDate);
        
        if (variantStats.isEmpty()) {
            return buildEmptyReport(expId, layer, startDate, endDate);
        }
        
        // Step 2: SRM 检验 - 验证分流比例
        ExperimentReport.SrmCheckResult srmResult = runSRMCheck(variantStats, controlVariantName, treatmentVariants, expectedProportions);
        
        // Step 3: 获取对照组统计
        MetricsRepository.VariantStats controlStats = variantStats.get(controlVariantName);
        if (controlStats == null) {
            return buildEmptyReport(expId, layer, startDate, endDate);
        }
        
        // Step 4: 主指标检验 - Z-Test（CUPED 增强）
        TestResult primaryResult = runPrimaryMetricTest(controlStats, variantStats, treatmentVariants);
        
        // Step 5: 辅助指标检验 - BH 校正
        List<TestResult> secondaryResults = runSecondaryMetricsTest(controlStats, variantStats, treatmentVariants);
        
        // Step 6: 护栏指标序贯检验 - mSPRT
        List<SequentialTestResult> guardrailResults = runGuardrailTests(controlStats, variantStats);
        
        // Step 7: 生成决策建议
        Recommendation recommendation = generateRecommendation(
            srmResult, primaryResult, secondaryResults, guardrailResults
        );
        
        // Step 8: 构建变体摘要
        Map<String, ExperimentReport.VariantSummary> summaries = buildVariantSummaries(variantStats, controlVariantName);
        
        // Step 9: 每日趋势数据
        Map<String, List<ExperimentReport.DailyMetric>> dailyTrends = buildDailyTrends(
            expId, controlVariantName, treatmentVariants, startDate, endDate
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
            .variantSummaries(summaries)
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
            Map<String, MetricsRepository.VariantStats> stats,
            String controlVariant,
            List<String> treatmentVariants,
            Map<String, Double> expectedProportions
    ) {
        List<String> allVariants = new ArrayList<>();
        allVariants.add(controlVariant);
        allVariants.addAll(treatmentVariants);
        
        long[] observed = new long[allVariants.size()];
        double[] expected = new double[allVariants.size()];
        Map<String, Long> observedCounts = new HashMap<>();
        
        long totalUsers = 0;
        for (int i = 0; i < allVariants.size(); i++) {
            MetricsRepository.VariantStats variantStats = stats.get(allVariants.get(i));
            long users = variantStats != null ? variantStats.getTotalUsers() : 0;
            observed[i] = users;
            observedCounts.put(allVariants.get(i), users);
            totalUsers += users;
        }
        
        // Use actual bucket proportions if available, fall back to equal split
        if (expectedProportions != null && !expectedProportions.isEmpty()) {
            for (int i = 0; i < allVariants.size(); i++) {
                expected[i] = expectedProportions.getOrDefault(allVariants.get(i), 1.0 / expected.length);
            }
        } else {
            for (int i = 0; i < expected.length; i++) {
                expected[i] = 1.0 / expected.length;
            }
        }
        
        double pValue = SrmTest.chiSquareTest(observed, expected);
        boolean passed = pValue >= 0.01; // SRM 阈值 1%
        
        Map<String, Double> expectedRatios = new HashMap<>();
        for (int i = 0; i < allVariants.size(); i++) {
            expectedRatios.put(allVariants.get(i), expected[i]);
        }
        
        return ExperimentReport.SrmCheckResult.builder()
            .passed(passed)
            .pValue(pValue)
            .chiSquareStatistic(0) // SrmTest 只返回 p-value
            .observedCounts(observedCounts)
            .expectedRatios(expectedRatios)
            .message(passed ? "SRM检验通过，分流比例正常" : "SRM检验失败，分流比例存在异常")
            .build();
    }
    
    /**
     * 主指标检验
     */
    private TestResult runPrimaryMetricTest(
            MetricsRepository.VariantStats controlStats,
            Map<String, MetricsRepository.VariantStats> variantStats,
            List<String> treatmentVariants
    ) {
        if (treatmentVariants.isEmpty()) {
            return null;
        }
        
        // 对第一个治疗组进行检验
        String treatmentVariant = treatmentVariants.get(0);
        MetricsRepository.VariantStats treatmentStats = variantStats.get(treatmentVariant);
        
        if (treatmentStats == null) {
            return null;
        }
        
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
            MetricsRepository.VariantStats controlStats,
            Map<String, MetricsRepository.VariantStats> variantStats,
            List<String> treatmentVariants
    ) {
        List<TestResult> results = new ArrayList<>();
        
        for (String treatmentVariant : treatmentVariants) {
            MetricsRepository.VariantStats treatmentStats = variantStats.get(treatmentVariant);
            if (treatmentStats == null) continue;
            
            TestResult result = zTest.executeProportion(
                controlStats.getTotalConversions(),
                controlStats.getTotalUsers(),
                treatmentStats.getTotalConversions(),
                treatmentStats.getTotalUsers()
            );
            result.setTestName("secondary_" + treatmentVariant);
            results.add(result);
        }
        
        // BH 校正
        return bhCorrection.correct(results);
    }
    
    /**
     * 护栏指标检验
     * TODO: Guardrail metrics should be read from experiment config (guardrail_metrics JSON)
     *       instead of hardcoding avgRevenue. The variance formula below is a placeholder —
     *       proper per-user revenue variance should be computed from ClickHouse.
     */
    private List<SequentialTestResult> runGuardrailTests(
            MetricsRepository.VariantStats controlStats,
            Map<String, MetricsRepository.VariantStats> variantStats
    ) {
        List<SequentialTestResult> results = new ArrayList<>();

        for (Map.Entry<String, MetricsRepository.VariantStats> entry : variantStats.entrySet()) {
            if (entry.getKey().equals("control")) continue;

            MetricsRepository.VariantStats treatmentStats = entry.getValue();

            // Revenue variance: use squared mean as rough proxy for continuous data.
            // The binomial p*(1-p) formula is not valid for revenue (continuous, can exceed 1.0).
            double ctrlVar = Math.max(controlStats.getAvgRevenue() * controlStats.getAvgRevenue(), 0.01);
            double treatVar = Math.max(treatmentStats.getAvgRevenue() * treatmentStats.getAvgRevenue(), 0.01);

            SampleStatistics control = SampleStatistics.builder()
                .n(controlStats.getTotalUsers())
                .mean(controlStats.getAvgRevenue())
                .variance(ctrlVar)
                .build();

            SampleStatistics treatment = SampleStatistics.builder()
                .n(treatmentStats.getTotalUsers())
                .mean(treatmentStats.getAvgRevenue())
                .variance(treatVar)
                .build();

            SequentialTestResult result = msprt.execute(control, treatment, (int) treatmentStats.getTotalUsers());
            result.setTestName("guardrail_" + entry.getKey());
            results.add(result);
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
     * 构建变体摘要
     */
    private Map<String, ExperimentReport.VariantSummary> buildVariantSummaries(
            Map<String, MetricsRepository.VariantStats> stats,
            String controlVariant
    ) {
        Map<String, ExperimentReport.VariantSummary> summaries = new HashMap<>();
        
        for (Map.Entry<String, MetricsRepository.VariantStats> entry : stats.entrySet()) {
            MetricsRepository.VariantStats variantStats = entry.getValue();
            
            summaries.put(entry.getKey(), ExperimentReport.VariantSummary.builder()
                .variant(entry.getKey())
                .totalUsers(variantStats.getTotalUsers())
                .totalConversions(variantStats.getTotalConversions())
                .conversionRate(variantStats.getConversionRate())
                .totalRevenue(variantStats.getTotalRevenue())
                .avgRevenuePerUser(variantStats.getAvgRevenue())
                .isControl(entry.getKey().equals(controlVariant))
                .build());
        }
        
        return summaries;
    }
    
    /**
     * 构建每日趋势
     */
    private Map<String, List<ExperimentReport.DailyMetric>> buildDailyTrends(
            String expId,
            String controlVariant,
            List<String> treatmentVariants,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<String, List<ExperimentReport.DailyMetric>> trends = new HashMap<>();
        
        // 查询各变体每日趋势
        List<String> allVariants = new ArrayList<>();
        allVariants.add(controlVariant);
        allVariants.addAll(treatmentVariants);
        
        for (String variant : allVariants) {
            Map<LocalDate, MetricsRepository.DailyStats> dailyStats = 
                metricsRepository.queryDailyTrend(expId, variant, startDate, endDate);
            
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
                trends.put(variant, dailyMetrics);
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