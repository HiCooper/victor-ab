package com.gateflow.victor.service.statistics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
import com.gateflow.victor.stats.engine.StatsEngine;
import com.gateflow.victor.stats.model.*;
import com.gateflow.victor.stats.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ExperimentReportServiceTest {

    @Mock private StatsEngine statsEngine;
    @Mock private ReportJobService jobService;
    @Mock private ReportRepository reportRepository;
    @Mock private ExperimentMapper experimentMapper;
    @Mock private VariantMapper variantMapper;
    @Mock private LayerMapper layerMapper;
    @Mock private ObjectMapper objectMapper;

    private ExperimentReportService service;

    private static final String EXP_ID = "exp_test_001";
    private static final LocalDate START = LocalDate.of(2026, 5, 19);
    private static final LocalDate END = LocalDate.of(2026, 5, 26);

    @BeforeEach
    void setUp() {
        service = new ExperimentReportService(
            statsEngine, jobService, reportRepository,
            experimentMapper, variantMapper, layerMapper, objectMapper
        );
    }

    @Test
    @DisplayName("getReport — 实验不存在时返回空报告")
    void shouldReturnEmptyReportWhenExperimentNotFound() {
        when(experimentMapper.selectByExpId(EXP_ID)).thenReturn(null);

        Map<String, Object> result = service.getReport(EXP_ID);

        assertEquals(EXP_ID, result.get("experimentId"));
        assertEquals("no_data", result.get("status"));
    }

    @Test
    @DisplayName("getReport — 无变体时返回空报告")
    void shouldReturnEmptyReportWhenNoVariants() {
        Experiment exp = buildExperiment(EXP_ID, "running");
        when(experimentMapper.selectByExpId(EXP_ID)).thenReturn(exp);
        when(layerMapper.selectById(anyLong())).thenReturn(buildLayer("web"));
        when(variantMapper.selectActiveVariants(EXP_ID)).thenReturn(List.of());

        Map<String, Object> result = service.getReport(EXP_ID);

        assertEquals(EXP_ID, result.get("experimentId"));
        assertEquals("no_data", result.get("status"));
    }

    @Test
    @DisplayName("getReport — 完整流程：生成报告并持久化")
    void shouldGenerateAndPersistReport() {
        Experiment exp = buildExperiment(EXP_ID, "ramp");
        Layer layer = buildLayer("web");
        Variant ctrl = buildVariant("control", 0, 4999, "control");
        Variant treat = buildVariant("treatment", 5000, 9999, "treatment");

        when(experimentMapper.selectByExpId(EXP_ID)).thenReturn(exp);
        when(layerMapper.selectById(anyLong())).thenReturn(layer);
        when(variantMapper.selectActiveVariants(EXP_ID)).thenReturn(List.of(ctrl, treat));

        ExperimentReport report = buildReport(EXP_ID, Recommendation.LAUNCH, true);
        when(statsEngine.analyzeExperiment(
            eq(EXP_ID), eq("web"), any(), any(),
            eq("control"), eq(List.of("treatment")), anyMap(), isNull()
        )).thenReturn(report);

        Map<String, Object> result = service.getReport(EXP_ID);

        assertNotNull(result);
        assertEquals(EXP_ID, result.get("experimentId"));
        // Verify SRM check present
        assertNotNull(result.get("srmCheck"));
        // Verify primary metric
        assertNotNull(result.get("primaryMetrics"));
        List<?> primary = (List<?>) result.get("primaryMetrics");
        assertFalse(primary.isEmpty());
        // Verify recommendation
        assertNotNull(result.get("recommendation"));
        // Verify persistence was called
        verify(reportRepository).saveReport(eq(report), any(), eq(false));
    }

    @Test
    @DisplayName("getReport — 持久化失败不影响报告返回")
    void shouldReturnReportEvenWhenPersistFails() {
        Experiment exp = buildExperiment(EXP_ID, "ramp");
        Layer layer = buildLayer("web");
        Variant ctrl = buildVariant("control", 0, 4999, "control");
        Variant treat = buildVariant("treatment", 5000, 9999, "treatment");

        when(experimentMapper.selectByExpId(EXP_ID)).thenReturn(exp);
        when(layerMapper.selectById(anyLong())).thenReturn(layer);
        when(variantMapper.selectActiveVariants(EXP_ID)).thenReturn(List.of(ctrl, treat));

        ExperimentReport report = buildReport(EXP_ID, Recommendation.CONTINUE_EXPERIMENT, false);
        when(statsEngine.analyzeExperiment(any(), any(), any(), any(), any(), any(), anyMap(), any()))
            .thenReturn(report);
        doThrow(new RuntimeException("DB down")).when(reportRepository).saveReport(any(), any(), anyBoolean());

        Map<String, Object> result = service.getReport(EXP_ID);

        assertNotNull(result);
        assertEquals(EXP_ID, result.get("experimentId"));
    }

    @Test
    @DisplayName("getHistoricalReport — 找到历史报告时返回")
    void shouldReturnHistoricalReportWhenFound() {
        ExperimentReport report = buildReport(EXP_ID, Recommendation.LAUNCH, true);
        when(reportRepository.findLatestReport(EXP_ID)).thenReturn(report);
        when(experimentMapper.selectByExpId(EXP_ID)).thenReturn(buildExperiment(EXP_ID, "running"));

        Map<String, Object> result = service.getHistoricalReport(EXP_ID);

        assertNotNull(result);
        assertEquals(EXP_ID, result.get("experimentId"));
    }

    @Test
    @DisplayName("getHistoricalReport — 未找到时返回空报告")
    void shouldReturnEmptyReportWhenNoHistory() {
        when(reportRepository.findLatestReport(EXP_ID)).thenReturn(null);

        Map<String, Object> result = service.getHistoricalReport(EXP_ID);

        assertEquals("no_data", result.get("status"));
    }

    @Test
    @DisplayName("getAllReports — 返回实验摘要列表")
    void shouldReturnExperimentSummaries() {
        Experiment e1 = buildExperiment("exp_1", "running");
        Experiment e2 = buildExperiment("exp_2", "ramp");
        when(experimentMapper.selectList(any())).thenReturn(List.of(e1, e2));

        List<Map<String, Object>> results = service.getAllReports();

        assertEquals(2, results.size());
        assertEquals("exp_1", results.get(0).get("experimentId"));
        assertEquals("exp_2", results.get(1).get("experimentId"));
    }

    @Test
    @DisplayName("triggerReportGeneration — 创建任务并返回 jobId")
    void shouldCreateJobAndReturnJobId() {
        String jobId = service.triggerReportGeneration(EXP_ID, null, null, null, null);

        assertNotNull(jobId);
        verify(jobService).createJob(eq(jobId), eq("report_generation"), eq(EXP_ID));
    }

    @Test
    @DisplayName("guardrail metrics 解析 — JSON 字符串数组格式")
    void shouldParseGuardrailMetricsStringArray() throws Exception {
        String json = "[\"avgRevenue\",\"conversionRate\"]";
        when(objectMapper.readValue(eq(json), any(TypeReference.class)))
            .thenReturn(List.of("avgRevenue", "conversionRate"));

        Experiment exp = buildExperiment(EXP_ID, "ramp");
        exp.setGuardrailMetrics(json);
        Layer layer = buildLayer("web");
        Variant ctrl = buildVariant("control", 0, 4999, "control");
        Variant treat = buildVariant("treatment", 5000, 9999, "treatment");

        when(experimentMapper.selectByExpId(EXP_ID)).thenReturn(exp);
        when(layerMapper.selectById(anyLong())).thenReturn(layer);
        when(variantMapper.selectActiveVariants(EXP_ID)).thenReturn(List.of(ctrl, treat));

        ExperimentReport report = buildReport(EXP_ID, Recommendation.LAUNCH, true);
        when(statsEngine.analyzeExperiment(eq(EXP_ID), any(), any(), any(), any(), any(), anyMap(), any()))
            .thenReturn(report);

        Map<String, Object> result = service.getReport(EXP_ID);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getReport — SRM 失败时建议不上线")
    void shouldReportSrmFailure() {
        Experiment exp = buildExperiment(EXP_ID, "ramp");
        Layer layer = buildLayer("web");
        Variant ctrl = buildVariant("control", 0, 4999, "control");
        Variant treat = buildVariant("treatment", 5000, 9999, "treatment");

        when(experimentMapper.selectByExpId(EXP_ID)).thenReturn(exp);
        when(layerMapper.selectById(anyLong())).thenReturn(layer);
        when(variantMapper.selectActiveVariants(EXP_ID)).thenReturn(List.of(ctrl, treat));

        ExperimentReport report = buildReport(EXP_ID, Recommendation.DO_NOT_LAUNCH, false);
        report.getSrmCheck().setPassed(false);
        report.getSrmCheck().setMessage("SRM检验失败");
        when(statsEngine.analyzeExperiment(any(), any(), any(), any(), any(), any(), anyMap(), any()))
            .thenReturn(report);

        Map<String, Object> result = service.getReport(EXP_ID);

        @SuppressWarnings("unchecked")
        Map<String, Object> srm = (Map<String, Object>) result.get("srmCheck");
        assertEquals(false, srm.get("passed"));
    }

    // ========== helpers ==========

    private Experiment buildExperiment(String expId, String status) {
        Experiment exp = new Experiment();
        exp.setId(1L);
        exp.setExpId(expId);
        exp.setName("Test Experiment");
        exp.setLayerId(1L);
        exp.setStatus(status);
        exp.setStartTime(LocalDateTime.of(2026, 5, 19, 0, 0));
        exp.setEndTime(LocalDateTime.of(2026, 5, 26, 0, 0));
        return exp;
    }

    private Layer buildLayer(String layerId) {
        Layer layer = new Layer();
        layer.setId(1L);
        layer.setLayerId(layerId);
        layer.setName("默认层");
        return layer;
    }

    private Variant buildVariant(String key, int bucketStart, int bucketEnd, String bucketId) {
        Variant v = new Variant();
        v.setId((long) bucketStart);
        v.setName(key);
        v.setBucketId(bucketId);
        v.setBucketStart(bucketStart);
        v.setBucketEnd(bucketEnd);
        return v;
    }

    private ExperimentReport buildReport(String expId, Recommendation rec, boolean srmPassed) {
        return ExperimentReport.builder()
            .expId(expId)
            .layer("web")
            .startDate(START)
            .endDate(END)
            .srmCheck(ExperimentReport.SrmCheckResult.builder()
                .passed(srmPassed)
                .pValue(0.5)
                .chiSquareStatistic(0.45)
                .observedCounts(Map.of("control", 5000L, "treatment", 5000L))
                .expectedRatios(Map.of("control", 0.5, "treatment", 0.5))
                .message("SRM检验通过")
                .build())
            .primaryMetric(TestResult.builder()
                .testName("z_test")
                .statistic(2.5)
                .pValue(0.012)
                .significant(true)
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
            .recommendation(rec)
            .recommendationReason("主指标统计显著且方向正向")
            .generatedAt(42)
            .build();
    }
}
