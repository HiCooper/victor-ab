package com.gateflow.victor.pipeline.aggregation;

import lombok.Data;
import lombok.Builder;

/**
 * 实验聚合指标数据模型
 */
@Data
@Builder
public class AggregatedMetric {
    private String expId;
    private String variant;
    private String layer;
    
    // 时间信息
    private String metricDate;
    private String minuteBucket;
    
    // 用户统计
    private long totalUsers;
    private long convertedUsers;
    
    // 事件统计
    private long totalEvents;
    private long conversions;
    
    // 转化率
    private double conversionRate;
    
    // 收入统计
    private double totalRevenue;
    private double avgRevenuePerUser;
    
    // 置信区间（Wilson score）
    private double confidenceIntervalLower;
    private double confidenceIntervalUpper;
    
    // 统计显著性
    private double significanceScore;
}
