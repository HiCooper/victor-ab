package com.gateflow.victor.domain.dto;

import lombok.Data;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 功效计算请求
 */
@Data
public class PowerAnalysisRequest {

    /**
     * 历史基准转化率 (0-1)
     */
    @NotNull(message = "baselineConversion 不能为空")
    @DecimalMin(value = "0.0", message = "baselineConversion 必须在 0-1 之间")
    @DecimalMax(value = "1.0", message = "baselineConversion 必须在 0-1 之间")
    private Double baselineConversion;

    /**
     * 最小可检测效应量 (MDE), 如 0.05 表示 5% 的相对提升
     */
    @NotNull(message = "mde 不能为空")
    @DecimalMin(value = "0.001", message = "mde 必须大于 0.1%")
    @DecimalMax(value = "1.0", message = "mde 必须小于 100%")
    private Double mde;

    /**
     * 显著性水平 alpha, 默认 0.05
     */
    @DecimalMin(value = "0.001", message = "alpha 必须大于 0.001")
    @DecimalMax(value = "0.1", message = "alpha 必须小于 0.1")
    private Double alpha;

    /**
     * 统计功效 power (1 - beta), 默认 0.8
     */
    @DecimalMin(value = "0.5", message = "power 必须大于 0.5")
    @DecimalMax(value = "0.99", message = "power 必须小于 0.99")
    private Double power;

    /**
     * 对照组流量占比, 默认 50
     */
    private Integer controlTrafficPercent;

    /**
     * 预估日均UV, 用于计算实验天数
     */
    private Long dailyUsers;
}
