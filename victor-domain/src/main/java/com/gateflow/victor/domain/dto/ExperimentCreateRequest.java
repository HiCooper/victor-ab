package com.gateflow.victor.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建实验请求DTO
 */
@Data
public class ExperimentCreateRequest {

    /**
     * 实验标识 (可选，不提供时自动生成)
     */
    @Size(max = 100, message = "expId长度不能超过100")
    private String expId;

    /**
     * 实验名称
     */
    @NotBlank(message = "name不能为空")
    @Size(max = 200, message = "name长度不能超过200")
    private String name;

    /**
     * 实验描述
     */
    private String description;

    /**
     * 所属层ID
     */
    @NotNull(message = "layerId不能为空")
    private Long layerId;

    /**
     * 桶起始位置 (0 - 9999)
     */
    @NotNull(message = "bucketStart不能为空")
    private Integer bucketStart;

    /**
     * 桶结束位置 (0 - 9999)
     */
    @NotNull(message = "bucketEnd不能为空")
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
     * 创建人
     */
    private String createdBy;

    /**
     * 版本配置列表
     */
    private List<VariantRequest> variants;

    /**
     * 版本请求DTO
     */
    @Data
    public static class VariantRequest {

        /**
         * 版本标识
         */
        @NotBlank(message = "variantKey不能为空")
        private String variantKey;

        /**
         * 版本名称
         */
        private String name;

        /**
         * 流量比例 (0-100)，前端传此值，后端自动计算bucketStart和bucketEnd
         */
        private Integer trafficPercentage;

        /**
         * 桶起始位置 (0 - 9999)
         * 如果传了trafficPercentage，此字段可选，后端自动计算
         */
        private Integer bucketStart;

        /**
         * 桶结束位置 (0 - 9999)
         * 如果传了trafficPercentage，此字段可选，后端自动计算
         */
        private Integer bucketEnd;

        /**
         * 版本参数 (JSON字符串)
         */
        private String params;
    }
}