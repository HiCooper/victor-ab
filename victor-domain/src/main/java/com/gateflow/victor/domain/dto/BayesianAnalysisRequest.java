package com.gateflow.victor.domain.dto;

import lombok.Data;

/**
 * 贝叶斯分析请求
 */
@Data
public class BayesianAnalysisRequest {

    /**
     * 实验ID
     */
    private Long experimentId;

    /**
     * 控制组样本数
     */
    private int controlSamples;

    /**
     * 控制组转化数
     */
    private int controlConversions;

    /**
     * 实验组样本数
     */
    private int treatmentSamples;

    /**
     * 实验组转化数
     */
    private int treatmentConversions;

    /**
     * 损失函数阈值 (默认0.001表示0.1%损失)
     */
    private double lossThreshold = 0.001;

    /**
     * 期望损失阈值 (风险控制)
     */
    private double expectedLossCutoff = 0.001;
}