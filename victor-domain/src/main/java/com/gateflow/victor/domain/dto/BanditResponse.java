package com.gateflow.victor.domain.dto;

import lombok.Data;

/**
 * 多臂老虎机响应
 */
@Data
public class BanditResponse {

    /**
     * 推荐variant ID
     */
    private Long recommendedVariantId;

    /**
     * 推荐variant名称
     */
    private String recommendedVariantName;

    /**
     * 每个variant的分配概率
     */
    private java.util.HashMap<String, Double> allocationProbabilities;

    /**
     * 每个variant的估计转化率
     */
    private java.util.HashMap<String, Double> estimatedRates;

    /**
     * 预期总转化率提升
     */
    private double expectedLift;

    /**
     * 算法说明
     */
    private String algorithmInfo;

    /**
     * 决策理由
     */
    private String reasoning;
}