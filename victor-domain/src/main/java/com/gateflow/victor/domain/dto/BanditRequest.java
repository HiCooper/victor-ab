package com.gateflow.victor.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 多臂老虎机请求
 */
@Data
public class BanditRequest {

    /**
     * 实验ID
     */
    private Long experimentId;

    /**
     * 算法类型: EPSILON_GREEDY, UCB, THOMPSON_SAMPLING
     */
    private String algorithm;

    /**
     * 探索率 (epsilon for Epsilon-Greedy)
     */
    private double epsilon = 0.1;

    /**
     * 是否启用自动调优
     */
    private boolean autoOptimize = true;

    /**
     * 最小样本量阈值 (开始分配前)
     */
    private int minSamples = 100;
}