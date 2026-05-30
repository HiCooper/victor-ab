package com.gateflow.victor.stats.model;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * ExperimentReport - 实验报告
 * 包含完整的统计分析结果和决策建议
 */
@Data
@Builder
public class ExperimentReport {
    
    private String expId;
    private String layer;
    
    // 时间范围
    private LocalDate startDate;
    private LocalDate endDate;
    
    // SRM 检验结果
    private SrmCheckResult srmCheck;
    
    // 主指标检验结果
    private TestResult primaryMetric;
    
    // 辅助指标检验结果
    private List<TestResult> secondaryMetrics;
    
    // 护栏指标检验结果
    private List<SequentialTestResult> guardrailMetrics;
    
    // 各变体统计
    private Map<String, VariantSummary> variantSummaries;
    
    // 每日趋势数据
    private Map<String, List<DailyMetric>> dailyTrends;
    
    // 推荐决策
    private Recommendation recommendation;
    
    // 决策原因
    private String recommendationReason;
    
    // CUPED 是否已应用
    private boolean cupedApplied;

    // 生成时间
    private long generatedAt;
    
    /**
     * SRM 检验结果
     */
    @Data
    @Builder
    public static class SrmCheckResult {
        private boolean passed;
        private double pValue;
        private double chiSquareStatistic;
        private Map<String, Long> observedCounts;
        private Map<String, Double> expectedRatios;
        private String message;
    }
    
    /**
     * 变体摘要
     */
    @Data
    @Builder
    public static class VariantSummary {
        private String variant;
        private long totalUsers;
        private long totalConversions;
        private double conversionRate;
        private double totalRevenue;
        private double avgRevenuePerUser;
        private boolean isControl;
        // CUPED 调整后的指标
        private Double cupedAdjustedMean;
        private Double cupedAdjustedVariance;
        // Tracker 行为指标 (跨库 JOIN)
        private com.gateflow.victor.stats.repository.MetricsRepository.BehaviorMetrics behaviorMetrics;
    }
    
    /**
     * 每日指标
     */
    @Data
    @Builder
    public static class DailyMetric {
        private LocalDate date;
        private long users;
        private long conversions;
        private double conversionRate;
        private double revenue;
    }
}