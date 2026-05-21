package com.gateflow.victor.domain.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 配置响应DTO - SDK拉取配置
 */
@Data
public class ConfigResponse {

    /**
     * 配置版本号
     */
    private String version;

    /**
     * 配置摘要 (ETag)
     */
    private String etag;

    /**
     * 变更类型 (FULL / INCREMENTAL)
     */
    private String changeType;

    /**
     * 实验配置列表
     */
    private List<ExperimentConfig> experiments;

    /**
     * 删除的实验ID列表 (仅增量更新时有效)
     */
    private List<String> deletedExperimentIds;

    /**
     * 实验配置模型
     */
    @Data
    public static class ExperimentConfig {
        /**
         * 实验标识
         */
        private String expId;

        /**
         * 所属层标识
         */
        private String layerId;

        /**
         * 层盐值
         */
        private String salt;

        /**
         * 桶起始位置
         */
        private Integer bucketStart;

        /**
         * 桶结束位置
         */
        private Integer bucketEnd;

        /**
         * 版本列表
         */
        private List<VariantConfig> variants;
    }

    /**
     * 版本配置模型
     */
    @Data
    public static class VariantConfig {
        /**
         * 分桶ID
         */
        @com.fasterxml.jackson.annotation.JsonProperty("variantKey")
        private String bucketId;

        /**
         * 桶起始位置
         */
        private Integer bucketStart;

        /**
         * 桶结束位置
         */
        private Integer bucketEnd;

        /**
         * 版本参数
         */
        private Map<String, Object> params;
    }
}