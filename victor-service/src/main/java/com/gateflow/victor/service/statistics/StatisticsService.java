package com.gateflow.victor.service.statistics;

import com.gateflow.victor.domain.dto.*;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.service.experiment.ExperimentService;
import com.gateflow.victor.stats.algorithm.ZTest;
import com.gateflow.victor.stats.model.TestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 实验统计分析服务
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {
    
    private final ExperimentService experimentService;
    private final ZTest zTest;
    
    /**
     * 获取实验指标结果
     */
    public ExperimentMetricsResponse getMetricResults(Long experimentId) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new RuntimeException("Experiment not found: " + experimentId);
        }
        
        // TODO: 从ClickHouse查询真实数据
        // 目前返回模拟数据用于演示
        return generateMockMetricResults(experiment);
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
        
        // TODO: 从ClickHouse查询真实时序数据
        return generateMockTimeSeriesData(experiment, d);
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
        
        // TODO: 从ClickHouse查询真实分桶数据
        return generateMockBucketStatistics(experiment, variants);
    }
    
    /**
     * 获取AA测试结果
     */
    public AATestResponse getAATestResults(Long experimentId) {
        Experiment experiment = experimentService.getExperiment(experimentId);
        if (experiment == null) {
            throw new RuntimeException("Experiment not found: " + experimentId);
        }
        
        // TODO: 从ClickHouse查询实验前7天数据进行AA测试
        return generateMockAATestResults(experiment);
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
        
        // TODO: 从ClickHouse查询真实流量数据
        return generateMockTrafficData(experiment, d);
    }
    
    // ========== 私有方法：生成模拟数据 ==========
    
    private ExperimentMetricsResponse generateMockMetricResults(Experiment experiment) {
        Random random = new Random(42); // 固定种子保证数据一致性
        
        // 主指标
        ExperimentMetricsResponse.MetricResult primary = ExperimentMetricsResponse.MetricResult.builder()
            .id("conversion_rate")
            .name("转化率")
            .value(0.152)
            .lift(12.5)
            .pValue(0.023)
            .significant(true)
            .direction("positive")
            .confidenceInterval(new double[]{0.08, 0.17})
            .build();
        
        // 辅助指标
        List<ExperimentMetricsResponse.MetricResult> secondary = List.of(
            ExperimentMetricsResponse.MetricResult.builder()
                .id("revenue_per_user")
                .name("每用户收入")
                .value(2.45)
                .lift(8.3)
                .pValue(0.045)
                .significant(true)
                .direction("positive")
                .confidenceInterval(new double[]{0.02, 0.15})
                .build(),
            ExperimentMetricsResponse.MetricResult.builder()
                .id("session_duration")
                .name("平均会话时长")
                .value(185.5)
                .lift(5.2)
                .pValue(0.125)
                .significant(false)
                .direction("neutral")
                .confidenceInterval(new double[]{-0.01, 0.11})
                .build()
        );
        
        // 护栏指标
        List<ExperimentMetricsResponse.MetricResult> guardrail = List.of(
            ExperimentMetricsResponse.MetricResult.builder()
                .id("page_load_time")
                .name("页面加载时间")
                .value(1.2)
                .lift(-2.1)
                .pValue(0.654)
                .significant(false)
                .direction("neutral")
                .confidenceInterval(new double[]{-0.05, 0.03})
                .build(),
            ExperimentMetricsResponse.MetricResult.builder()
                .id("error_rate")
                .name("错误率")
                .value(0.008)
                .lift(1.5)
                .pValue(0.723)
                .significant(false)
                .direction("neutral")
                .confidenceInterval(new double[]{-0.02, 0.04})
                .build()
        );
        
        return ExperimentMetricsResponse.builder()
            .primary(primary)
            .secondary(secondary)
            .guardrail(guardrail)
            .build();
    }
    
    private TimeSeriesDataResponse generateMockTimeSeriesData(Experiment experiment, int days) {
        List<TimeSeriesDataResponse.DataPoint> data = new ArrayList<>();
        LocalDate startDate = experiment.getStartTime() != null 
            ? experiment.getStartTime().toLocalDate() 
            : LocalDate.now().minusDays(days);
        
        Random random = new Random(42);
        
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            double control = 0.12 + random.nextDouble() * 0.04;
            double treatment = 0.14 + random.nextDouble() * 0.04;
            
            data.add(TimeSeriesDataResponse.DataPoint.builder()
                .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .control(Math.round(control * 1000.0) / 1000.0)
                .treatment(Math.round(treatment * 1000.0) / 1000.0)
                .build());
        }
        
        return TimeSeriesDataResponse.builder()
            .data(data)
            .build();
    }
    
    private BucketStatisticsResponse generateMockBucketStatistics(Experiment experiment, List<Variant> variants) {
        List<BucketStatisticsResponse.BucketStat> buckets = new ArrayList<>();
        
        // 为每个variant生成统计数据
        for (Variant variant : variants) {
            BucketStatisticsResponse.BucketStat stat = BucketStatisticsResponse.BucketStat.builder()
                .bucket(variant.getName() != null ? variant.getName() : variant.getVariantKey())
                .dailyEntries(1250L)
                .dailySamples(8500L)
                .chiSquarePValue(0.856)
                .cumulativeSamples(59500L)
                .cumulativeChiSquarePValue(0.923)
                .build();
            buckets.add(stat);
        }
        
        // SRM检验
        boolean srmPassed = true;
        String srmMessage = "所有分桶的卡方检验P值均大于0.01，分流比例均衡，样本分配无显著偏差。";
        
        return BucketStatisticsResponse.builder()
            .buckets(buckets)
            .srmPassed(srmPassed)
            .srmMessage(srmMessage)
            .build();
    }
    
    private AATestResponse generateMockAATestResults(Experiment experiment) {
        List<AATestResponse.AATestResult> results = List.of(
            AATestResponse.AATestResult.builder()
                .metric("转化率")
                .controlMean(0.125)
                .variantMean(0.127)
                .pValue(0.654)
                .significant(false)
                .build(),
            AATestResponse.AATestResult.builder()
                .metric("每用户收入")
                .controlMean(2.35)
                .variantMean(2.38)
                .pValue(0.723)
                .significant(false)
                .build(),
            AATestResponse.AATestResult.builder()
                .metric("平均会话时长")
                .controlMean(182.5)
                .variantMean(184.2)
                .pValue(0.812)
                .significant(false)
                .build()
        );
        
        boolean aaTestPassed = true;
        String message = "所有核心指标P值均大于0.05，实验前各组无显著差异，AA测试通过。";
        
        return AATestResponse.builder()
            .results(results)
            .aaTestPassed(aaTestPassed)
            .message(message)
            .build();
    }
    
    private TrafficDataResponse generateMockTrafficData(Experiment experiment, int days) {
        List<TrafficDataResponse.DataPoint> data = new ArrayList<>();
        LocalDate startDate = experiment.getStartTime() != null 
            ? experiment.getStartTime().toLocalDate() 
            : LocalDate.now().minusDays(days);
        
        Random random = new Random(42);
        
        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            long control = 4200 + random.nextInt(300);
            long treatment = 4300 + random.nextInt(300);
            
            data.add(TrafficDataResponse.DataPoint.builder()
                .date(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .control(control)
                .treatment(treatment)
                .build());
        }
        
        return TrafficDataResponse.builder()
            .data(data)
            .build();
    }
}
