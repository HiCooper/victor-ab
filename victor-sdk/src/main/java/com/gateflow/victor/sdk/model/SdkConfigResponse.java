package com.gateflow.victor.sdk.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 配置响应模型 - SDK内部使用
 * <p>
 * 从服务端拉取的实验配置数据结构
 */
@Data
public class SdkConfigResponse {

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
        private List<BucketConfig> buckets;
    }

    /**
     * 版本配置模型
     */
    @Data
    public static class BucketConfig {
        /**
         * 版本标识
         */
        private String bucketKey;

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