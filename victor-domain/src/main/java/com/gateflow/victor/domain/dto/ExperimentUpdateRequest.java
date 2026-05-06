package com.gateflow.victor.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新实验请求DTO
 */
@Data
public class ExperimentUpdateRequest {

    /**
     * 实验名称
     */
    @Size(max = 200, message = "name长度不能超过200")
    private String name;

    /**
     * 实验描述
     */
    private String description;

    /**
     * 桶起始位置 (0 - 9999)
     */
    private Integer bucketStart;

    /**
     * 桶结束位置 (0 - 9999)
     */
    private Integer bucketEnd;

    /**
     * 定向规则 (JSON字符串)
     */
    private String targetingRules;

    /**
     * 主指标
     */
    private String primaryMetric;

    /**
     * 次指标列表 (JSON字符串)
     */
    private String secondaryMetrics;

    /**
     * 护栏指标列表 (JSON字符串)
     */
    private String guardrailMetrics;

    /**
     * 版本配置列表（更新时创建新版本）
     */
    private List<ExperimentCreateRequest.VariantRequest> variants;
}