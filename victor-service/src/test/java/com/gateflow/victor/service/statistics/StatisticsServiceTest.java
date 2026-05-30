package com.gateflow.victor.service.statistics;

import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.dto.ExperimentMetricsResponse;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.BucketMapper;
import com.gateflow.victor.service.experiment.ExperimentService;
import com.gateflow.victor.stats.algorithm.ZTest;
import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.model.*;
import com.gateflow.victor.stats.repository.MetricsRepository;
import com.gateflow.victor.stats.repository.ReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock private ExperimentService experimentService;
    @Mock private BucketMapper bucketMapper;
    @Mock private LayerMapper layerMapper;
    @Mock private StatsEngine statsEngine;
    @Mock private ReportRepository reportRepository;
    @Mock private MetricsRepository metricsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private StatisticsService service;

    private static final Long EXP_ID = 1L;
    private static final String BUSINESS_EXP_ID = "exp_test_001";

    @BeforeEach
    void setUp() {
        ZTest zTest = new ZTest();
        service = new StatisticsService(
            experimentService, bucketMapper, layerMapper,
            statsEngine, reportRepository, zTest, objectMapper
        );
    }

    @Test
    @DisplayName("getMetricResults — 实验不存在时抛出 VictorException")
    void shouldThrowWhenExperimentNotFound() {
        when(experimentService.getExperiment(EXP_ID)).thenReturn(null);

        VictorException ex = assertThrows(VictorException.class,
            () -> service.getMetricResults(EXP_ID));
        assertEquals(ErrorCode.EXP_NOT_FOUND.getCode(), ex.getErrorCode());
    }

    @Test
    @DisplayName("getMetricResults — 无变体时返回空指标")
    void shouldReturnEmptyMetricsWhenNoVariants() {
        Experiment exp = buildExperiment();
        when(experimentService.getExperiment(EXP_ID)).thenReturn(exp);
        when(reportRepository.findLatestCupedValues(BUSINESS_EXP_ID)).thenReturn(Map.of());
        when(experimentService.getExperimentVariants(EXP_ID)).thenReturn(List.of());
        when(layerMapper.selectById(anyLong())).thenReturn(null);

        ExperimentMetricsResponse result = service.getMetricResults(EXP_ID);

        assertNotNull(result);
        assertEquals(0.0, result.getPrimary().getValue());
        assertEquals("neutral", result.getPrimary().getDirection());
    }

    @Test
    @DisplayName("getMetricResults — 正常流程返回主指标数据")
    void shouldReturnPrimaryMetricWithLift() {
        Experiment exp = buildExperiment();
        List<Bucket> variants = List.of(
            buildVariant("control", 0, 4999, "control"),
            buildVariant("treatment", 5000, 9999, "treatment")
        );

        when(experimentService.getExperiment(EXP_ID)).thenReturn(exp);
        when(reportRepository.findLatestCupedValues(BUSINESS_EXP_ID)).thenReturn(Map.of());
        when(experimentService.getExperimentVariants(EXP_ID)).thenReturn(variants);
        when(layerMapper.selectById(anyLong())).thenReturn(null);

        ExperimentReport report = buildReport();
        when(statsEngine.analyzeExperiment(any(), any(), any(), any(), any(), any(), anyMap(), isNull()))
            .thenReturn(report);

        ExperimentMetricsResponse result = service.getMetricResults(EXP_ID);

        assertNotNull(result);
        assertTrue(result.getPrimary().isSignificant());
        assertTrue(result.getPrimary().getLift() > 0, "lift should be positive");
    }

    @Test
    @DisplayName("getMetricResults — 主指标不显著时不建议上线")
    void shouldReturnNonSignificantWhenNoEffect() {
        Experiment exp = buildExperiment();
        List<Bucket> variants = List.of(
            buildVariant("control", 0, 4999, "control"),
            buildVariant("treatment", 5000, 9999, "treatment")
        );

        when(experimentService.getExperiment(EXP_ID)).thenReturn(exp);
        when(reportRepository.findLatestCupedValues(BUSINESS_EXP_ID)).thenReturn(Map.of());
        when(experimentService.getExperimentVariants(EXP_ID)).thenReturn(variants);
        when(layerMapper.selectById(anyLong())).thenReturn(null);

        ExperimentReport report = buildNonSignificantReport();
        when(statsEngine.analyzeExperiment(any(), any(), any(), any(), any(), any(), anyMap(), isNull()))
            .thenReturn(report);

        ExperimentMetricsResponse result = service.getMetricResults(EXP_ID);

        assertNotNull(result);
        assertFalse(result.getPrimary().isSignificant());
    }

    // ========== helpers ==========

    private Experiment buildExperiment() {
        Experiment exp = new Experiment();
        exp.setId(EXP_ID);
        exp.setExpId(BUSINESS_EXP_ID);
        exp.setName("Test Experiment");
        exp.setLayerId(1L);
        exp.setStatus("running");
        exp.setStartTime(LocalDateTime.of(2026, 5, 19, 0, 0));
        exp.setEndTime(LocalDateTime.of(2026, 5, 26, 0, 0));
        return exp;
    }

    private Variant buildVariant(String key, int bucketStart, int bucketEnd, String bucketId) {
        Variant v = new Bucket();
        v.setId((long) bucketStart);
        v.setName(key);
        v.setBucketId(bucketId);
        v.setBucketStart(bucketStart);
        v.setBucketEnd(bucketEnd);
        return v;
    }

    private ExperimentReport buildReport() {
        return ExperimentReport.builder()
            .expId(BUSINESS_EXP_ID)
            .layer("web")
            .startDate(LocalDate.of(2026, 5, 19))
            .endDate(LocalDate.of(2026, 5, 26))
            .srmCheck(ExperimentReport.SrmCheckResult.builder()
                .passed(true).pValue(0.5).message("SRM通过").build())
            .primaryMetric(TestResult.builder()
                .testName("z_test").statistic(2.5).pValue(0.012).significant(true)
                .lift(LiftEstimate.of(0.26, 0.05, 0.47))
                .confidenceInterval(ConfidenceInterval.of(0.01, 0.04, 0.95))
                .build())
            .secondaryMetrics(List.of())
            .guardrailMetrics(List.of())
            .variantSummaries(Map.of(
                "control", ExperimentReport.VariantSummary.builder()
                    .variant("control").totalUsers(5000).totalConversions(115)
                    .conversionRate(0.023).avgRevenuePerUser(2.5).isControl(true).build(),
                "treatment", ExperimentReport.VariantSummary.builder()
                    .variant("treatment").totalUsers(5000).totalConversions(145)
                    .conversionRate(0.029).avgRevenuePerUser(2.5).isControl(false).build()
            ))
            .dailyTrends(Map.of())
            .recommendation(com.gateflow.victor.stats.model.Recommendation.LAUNCH)
            .recommendationReason("主指标统计显著且方向正向")
            .generatedAt(42)
            .build();
    }

    private ExperimentReport buildNonSignificantReport() {
        return ExperimentReport.builder()
            .expId(BUSINESS_EXP_ID)
            .layer("web")
            .startDate(LocalDate.of(2026, 5, 19))
            .endDate(LocalDate.of(2026, 5, 26))
            .srmCheck(ExperimentReport.SrmCheckResult.builder()
                .passed(true).pValue(0.5).message("SRM通过").build())
            .primaryMetric(TestResult.builder()
                .testName("z_test").statistic(0.8).pValue(0.42).significant(false)
                .lift(LiftEstimate.of(0.02, -0.10, 0.14))
                .confidenceInterval(ConfidenceInterval.of(-0.002, 0.003, 0.95))
                .build())
            .secondaryMetrics(List.of())
            .guardrailMetrics(List.of())
            .variantSummaries(Map.of(
                "control", ExperimentReport.VariantSummary.builder()
                    .variant("control").totalUsers(5000).totalConversions(115)
                    .conversionRate(0.023).avgRevenuePerUser(2.5).isControl(true).build(),
                "treatment", ExperimentReport.VariantSummary.builder()
                    .variant("treatment").totalUsers(5000).totalConversions(118)
                    .conversionRate(0.0236).avgRevenuePerUser(2.5).isControl(false).build()
            ))
            .dailyTrends(Map.of())
            .recommendation(com.gateflow.victor.stats.model.Recommendation.CONTINUE_EXPERIMENT)
            .recommendationReason("样本量不足")
            .generatedAt(35)
            .build();
    }
}
