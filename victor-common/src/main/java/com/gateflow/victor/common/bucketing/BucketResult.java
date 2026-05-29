package com.gateflow.victor.common.bucketing;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分桶结果
 */
@Data
@NoArgsConstructor
public class BucketResult {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 实验标识
     */
    private String experimentKey;

    /**
     * 桶号 (0-9999)
     */
    private Integer bucket;

    /**
     * 分配的版本标识 (null表示未命中)
     */
    private String variant;

    /**
     * 版本参数 (JSON格式，命中时返回对应分桶的参数配置)
     */
    private String params;

    /**
     * 层标识
     */
    private String layerId;

    /**
     * 是否命中实验
     */
    private boolean hit;

    /**
     * 创建命中结果（含参数）
     */
    public static BucketResult hit(String userId, String experimentKey, int bucket,
                                    String variant, String layerId, String params) {
        BucketResult r = new BucketResult();
        r.userId = userId;
        r.experimentKey = experimentKey;
        r.bucket = bucket;
        r.variant = variant;
        r.layerId = layerId;
        r.params = params;
        r.hit = true;
        return r;
    }

    /**
     * 创建未命中结果
     */
    public static BucketResult notHit(String userId, String experimentKey, int bucket) {
        BucketResult r = new BucketResult();
        r.userId = userId;
        r.experimentKey = experimentKey;
        r.bucket = bucket;
        r.hit = false;
        return r;
    }

    /**
     * 获取版本 (未命中时返回null)
     */
    public String getVariantOrNull() {
        return hit ? variant : null;
    }

    /**
     * 获取参数 (未命中时返回null)
     */
    public String getParamsOrNull() {
        return hit ? params : null;
    }
}