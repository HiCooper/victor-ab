package com.gateflow.victor.domain.dto;

import lombok.Data;

/**
 * 贝叶斯分析结果
 */
@Data
public class BayesianAnalysisResponse {

    /**
     * 实验组相对控制组的提升率
     */
    private double lift;

    /**
     * 提升率可信区间下限 (2.5%)
     */
    private double liftCiLower;

    /**
     * 提升率可信区间上限 (97.5%)
     */
    private double liftCiUpper;

    /**
     * 实验组更好于控制组的概率
     */
    private double probabilityBetter;

    /**
     * 期望损失 (Expected Loss)
     */
    private double expectedLoss;

    /**
     * 需要的最小样本量 (基于当前趋势)
     */
    private int requiredSamples;

    /**
     * 是否可以停止实验
     */
    private boolean canStop;

    /**
     * 建议: CONTINUE(继续), STOP_BEST(停止选择最优), STOP_REJECT(停止丢弃实验组)
     */
    private String recommendation;

    /**
     * 分析摘要
     */
    private String summary;
}