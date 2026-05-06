package com.gateflow.victor.bucketing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分桶结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
     * 层标识
     */
    private String layerId;

    /**
     * 是否命中实验
     */
    private boolean hit;

    /**
     * 创建命中结果
     */
    public static BucketResult hit(String userId, String experimentKey, int bucket, 
                                    String variant, String layerId) {
        return new BucketResult(userId, experimentKey, bucket, variant, layerId, true);
    }

    /**
     * 创建未命中结果
     */
    public static BucketResult notHit(String userId, String experimentKey, int bucket) {
        return new BucketResult(userId, experimentKey, bucket, null, null, false);
    }

    /**
     * 获取版本 (未命中时返回null)
     */
    public String getVariantOrNull() {
        return hit ? variant : null;
    }
}