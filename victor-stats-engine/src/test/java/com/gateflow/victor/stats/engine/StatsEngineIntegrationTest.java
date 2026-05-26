package com.gateflow.victor.stats.engine;

import com.gateflow.victor.stats.algorithm.*;
import com.gateflow.victor.stats.model.*;
import com.gateflow.victor.stats.repository.MetricsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsEngineIntegrationTest {

    @Mock
    private MetricsRepository metricsRepository;

    private StatsEngine statsEngine;

    private static final String EXP_ID = "exp_test_001";
    private static final String LAYER = "web";
    private static final String CONTROL = "control";
    private static final String TREATMENT = "treatment";
    private static final LocalDate START = LocalDate.of(2026, 5, 19);
    private static final LocalDate END = LocalDate.of(2026, 5, 26);

    @BeforeEach
    void setUp() {
        ZTest zTest = new ZTest();
        CUPED cuped = new CUPED();
        BHCorrection bhCorrection = new BHCorrection();
        mSPRT msprt = new mSPRT();
        statsEngine = new StatsEngine(zTest, cuped, bhCorrection, msprt, metricsRepository);
    }

    @Test
    @DisplayName("完整分析流水线 — 显著正向结果 → 建议上线")
    void shouldRecommendLaunchWhenPrimaryMetricSignificantlyPositive() {
        // Arrange: control 2.3% CVR, treatment 2.9% CVR, 10k users each
        MetricsRepository.VariantStats controlStats = buildVariantStats(CONTROL, 10000, 230, 0, 25000);
        MetricsRepository.VariantStats treatStats = buildVariantStats(TREATMENT, 10000, 290, 0, 25000);

        Map<String, MetricsRepository.VariantStats> stats = new LinkedHashMap<>();
        stats.put(CONTROL, controlStats);
        stats.put(TREATMENT, treatStats);

        when(metricsRepository.queryExperimentStats(eq(EXP_ID), any(), any()))
            .thenReturn(stats);

        // Act
        ExperimentReport report = statsEngine.analyzeExperiment(
            EXP_ID, LAYER, START, END, CONTROL,
            List.of(TREATMENT), Map.of(CONTROL, 0.5, TREATMENT, 0.5), null
        );

        // Assert
        assertNotNull(report);
        assertEquals(EXP_ID, report.getExpId());

        // SRM should pass (50:50 split with 10k each)
        assertNotNull(report.getSrmCheck());
        assertTrue(report.getSrmCheck().isPassed(), "SRM should pass with equal traffic");

        // Primary metric should be significant with positive lift
        assertNotNull(report.getPrimaryMetric());
        assertTrue(report.getPrimaryMetric().isSignificant(),
            "Primary metric should be significant");
        assertNotNull(report.getPrimaryMetric().getLift());
        assertTrue(report.getPrimaryMetric().getLift().getValue() > 0,
            "Lift should be positive");

        // Guardrail should not block launch (no negative guardrail)
        if (report.getGuardrailMetrics() != null) {
            for (SequentialTestResult g : report.getGuardrailMetrics()) {
                assertNotEquals(SequentialStatus.STOP_NEGATIVE, g.getStatus(),
                    "Guardrail should not be STOP_NEGATIVE");
            }
        }

        // Recommendation should be LAUNCH
        assertEquals(Recommendation.LAUNCH, report.getRecommendation(),
            "Should recommend LAUNCH when primary metric is significantly positive");
    }

    @Test
    @DisplayName("完整分析流水线 — 无显著差异 → 建议继续实验")
    void shouldRecommendContinueWhenNoSignificantDifference() {
        // Arrange: both groups have ~2.3% CVR, no real difference
        MetricsRepository.VariantStats controlStats = buildVariantStats(CONTROL, 500, 12, 0, 500);
        MetricsRepository.VariantStats treatStats = buildVariantStats(TREATMENT, 500, 11, 0, 500);

        Map<String, MetricsRepository.VariantStats> stats = new LinkedHashMap<>();
        stats.put(CONTROL, controlStats);
        stats.put(TREATMENT, treatStats);

        when(metricsRepository.queryExperimentStats(eq(EXP_ID), any(), any()))
            .thenReturn(stats);

        // Act
        ExperimentReport report = statsEngine.analyzeExperiment(
            EXP_ID, LAYER, START, END, CONTROL,
            List.of(TREATMENT), Map.of(CONTROL, 0.5, TREATMENT, 0.5), null
        );

        // Assert
        assertNotNull(report);
        // With small sample and no real difference, should not recommend launch
        assertNotEquals(Recommendation.LAUNCH, report.getRecommendation(),
            "Should not recommend LAUNCH without significant result");
    }

    @Test
    @DisplayName("完整分析流水线 — SRM 失败 → 建议不上线")
    void shouldRecommendDoNotLaunchWhenSrmFails() {
        // Arrange: large SRM mismatch (expected 50:50 but actual 90:10)
        MetricsRepository.VariantStats controlStats = buildVariantStats(CONTROL, 9000, 207, 0, 25000);
        MetricsRepository.VariantStats treatStats = buildVariantStats(TREATMENT, 1000, 29, 0, 25000);

        Map<String, MetricsRepository.VariantStats> stats = new LinkedHashMap<>();
        stats.put(CONTROL, controlStats);
        stats.put(TREATMENT, treatStats);

        when(metricsRepository.queryExperimentStats(eq(EXP_ID), any(), any()))
            .thenReturn(stats);

        // Act
        ExperimentReport report = statsEngine.analyzeExperiment(
            EXP_ID, LAYER, START, END, CONTROL,
            List.of(TREATMENT), Map.of(CONTROL, 0.5, TREATMENT, 0.5), null
        );

        // Assert
        assertNotNull(report.getSrmCheck());
        assertFalse(report.getSrmCheck().isPassed(),
            "SRM should fail with 90:10 split vs expected 50:50");
        assertEquals(Recommendation.DO_NOT_LAUNCH, report.getRecommendation(),
            "Should recommend DO_NOT_LAUNCH when SRM fails");
    }

    @Test
    @DisplayName("单独算法 — ZTest 比例检验")
    void shouldRunZTestCorrectly() {
        // ~23% conversion rate with 10k samples → clear signal
        TestResult result = statsEngine.runZTest(230, 1000, 290, 1000);

        assertNotNull(result);
        assertTrue(result.isSignificant());
        assertNotNull(result.getLift());
        assertTrue(result.getLift().getValue() > 0);
        assertTrue(result.getPValue() < 0.05);
    }

    @Test
    @DisplayName("单独算法 — SRM 卡方检验")
    void shouldRunSrmTestCorrectly() {
        // Equal traffic → should pass
        double pValue = statsEngine.runSRM(
            new long[]{5000, 5000},
            new double[]{0.5, 0.5}
        );
        assertTrue(pValue > 0.01, "Equal traffic should pass SRM");

        // Skewed traffic → should fail
        double pValueSkewed = statsEngine.runSRM(
            new long[]{9000, 1000},
            new double[]{0.5, 0.5}
        );
        assertTrue(pValueSkewed < 0.01, "Skewed traffic should fail SRM");
    }

    @Test
    @DisplayName("单独算法 — mSPRT 护栏检验")
    void shouldRunMsprtCorrectly() {
        SampleStatistics control = SampleStatistics.builder()
            .n(500).mean(10.0).variance(25.0).build();
        SampleStatistics treatment = SampleStatistics.builder()
            .n(500).mean(10.1).variance(25.0).build();

        SequentialTestResult result = statsEngine.runmSPRT(control, treatment, 500);

        assertNotNull(result);
        assertEquals("mSPRT", result.getTestName());
        assertTrue(result.getLambda() > 0);
        assertTrue(result.getUpperBound() > result.getLowerBound());
    }

    @Test
    @DisplayName("空数据 → 返回空报告")
    void shouldReturnEmptyReportWhenNoData() {
        when(metricsRepository.queryExperimentStats(eq(EXP_ID), any(), any()))
            .thenReturn(Collections.emptyMap());

        ExperimentReport report = statsEngine.analyzeExperiment(
            EXP_ID, LAYER, START, END, CONTROL,
            List.of(TREATMENT), Map.of(CONTROL, 0.5, TREATMENT, 0.5), null
        );

        assertNotNull(report);
        assertEquals(Recommendation.INCONCLUSIVE, report.getRecommendation());
    }

    private MetricsRepository.VariantStats buildVariantStats(
        String variant, long users, long conversions, long events, double revenue
    ) {
        MetricsRepository.VariantStats s = new MetricsRepository.VariantStats();
        s.setVariant(variant);
        s.setLayer(LAYER);
        s.setTotalUsers(users);
        s.setTotalConversions(conversions);
        s.setTotalEvents(events > 0 ? events : users);
        s.setTotalRevenue(revenue);
        if (users > 0) {
            s.setConversionRate((double) conversions / users);
            s.setAvgRevenue(revenue / users);
        }
        return s;
    }
}
