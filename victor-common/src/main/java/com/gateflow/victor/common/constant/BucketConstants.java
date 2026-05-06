package com.gateflow.victor.common.constant;

/**
 * 分桶常量定义
 */
public final class BucketConstants {

    /**
     * 总桶数 - 10000桶，支持0.1%粒度流量分配
     */
    public static final int TOTAL_BUCKETS = 10000;

    /**
     * 最小流量粒度 - 0.1%
     */
    public static final double MIN_GRANULARITY = 0.001;

    /**
     * 默认盐值
     */
    public static final String DEFAULT_SALT = "victor";

    /**
     * 分桶哈希输入格式: userId#layerId#salt
     */
    public static final String HASH_INPUT_FORMAT = "%s#%s#%s";

    private BucketConstants() {
        // 常量类，禁止实例化
    }
}