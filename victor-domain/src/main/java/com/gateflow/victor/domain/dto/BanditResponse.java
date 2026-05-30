package com.gateflow.victor.domain.dto;

import lombok.Data;

/**
 * 多臂老虎机响应
 */
@Data
public class BanditResponse {

    /**
     * 推荐bucket ID
     */
    private Long recommendedBucketId;

    /**
     * 推荐bucket名称
     */
    private String recommendedBucketName;

    /**
     * 每个bucket的分配概率
     */
    private java.util.HashMap<String, Double> allocationProbabilities;

    /**
     * 每个bucket的估计转化率
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