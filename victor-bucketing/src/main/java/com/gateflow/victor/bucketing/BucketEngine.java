package com.gateflow.victor.bucketing;

import com.gateflow.victor.common.constant.BucketConstants;
import com.gateflow.victor.common.util.MurmurHash3;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 分桶引擎 - 核心分流计算
 * 
 * 纯Java实现，无Spring依赖，可直接移植到SDK
 */
public final class BucketEngine {

    private BucketEngine() {
        // 工具类，禁止实例化
    }

    /**
     * 计算用户桶号
     *
     * @param userId  用户ID
     * @param layerId 层ID
     * @param salt    盐值 (用于层间正交)
     * @return 桶号 (0-9999)
     */
    public static int computeBucket(String userId, String layerId, String salt) {
        // 使用 MurmurHash3 计算哈希值
        // 格式: userId#layerId#salt
        String hashInput = String.format(BucketConstants.HASH_INPUT_FORMAT, userId, layerId, salt);
        int hash = MurmurHash3.hash32(hashInput.getBytes(StandardCharsets.UTF_8));
        
        // 取模得到桶号 (0 - TOTAL_BUCKETS-1)
        return Math.abs(hash) % BucketConstants.TOTAL_BUCKETS;
    }

    /**
     * 根据桶号查找版本
     *
     * @param bucket       桶号 (0-9999)
     * @param variantSpecs 版本规格列表
     * @return 版本标识 (null表示未命中)
     */
    public static String findVariant(int bucket, List<VariantSpec> variantSpecs) {
        if (variantSpecs == null || variantSpecs.isEmpty()) {
            return null;
        }

        for (VariantSpec spec : variantSpecs) {
            if (bucket >= spec.getBucketStart() && bucket <= spec.getBucketEnd()) {
                return spec.getBucketId();
            }
        }

        return null; // 未命中任何版本
    }

    /**
     * 判断用户是否命中实验
     *
     * @param bucket       用户桶号
     * @param experiment   实验规格
     * @return true表示命中
     */
    public static boolean isInExperiment(int bucket, ExperimentSpec experiment) {
        return bucket >= experiment.getBucketStart() && bucket <= experiment.getBucketEnd();
    }

    /**
     * 计算用户在指定实验中的版本
     *
     * @param userId     用户ID
     * @param experiment 实验规格
     * @return 分桶结果
     */
    public static BucketResult computeBucketResult(String userId, ExperimentSpec experiment) {
        // 计算用户桶号
        int bucket = computeBucket(userId, experiment.getLayerId(), experiment.getSalt());

        // 判断是否命中实验
        if (!isInExperiment(bucket, experiment)) {
            return BucketResult.notHit(userId, experiment.getExpId(), bucket);
        }

        // 查找版本
        String variant = findVariant(bucket, experiment.getVariants());

        if (variant == null) {
            return BucketResult.notHit(userId, experiment.getExpId(), bucket);
        }

        return BucketResult.hit(userId, experiment.getExpId(), bucket, variant, experiment.getLayerId());
    }

    /**
     * 批量计算用户所有实验的分桶结果
     *
     * @param userId      用户ID
     * @param experiments 实验规格列表
     * @return 分桶结果列表
     */
    public static List<BucketResult> computeAllBucketResults(String userId, List<ExperimentSpec> experiments) {
        return experiments.stream()
                .map(exp -> computeBucketResult(userId, exp))
                .toList();
    }

    /**
     * 实验规格 - 用于分桶计算的实验配置
     */
    public static class ExperimentSpec {
        private final String expId;
        private final String layerId;
        private final String salt;
        private final int bucketStart;
        private final int bucketEnd;
        private final List<VariantSpec> variants;

        public ExperimentSpec(String expId, String layerId, String salt,
                             int bucketStart, int bucketEnd, List<VariantSpec> variants) {
            this.expId = expId;
            this.layerId = layerId;
            this.salt = salt;
            this.bucketStart = bucketStart;
            this.bucketEnd = bucketEnd;
            this.variants = variants;
        }

        public String getExpId() { return expId; }
        public String getLayerId() { return layerId; }
        public String getSalt() { return salt; }
        public int getBucketStart() { return bucketStart; }
        public int getBucketEnd() { return bucketEnd; }
        public List<VariantSpec> getVariants() { return variants; }
    }

    /**
     * 版本规格 - 用于分桶计算的版本配置
     */
    public static class VariantSpec {
        private final String bucketId;
        private final int bucketStart;
        private final int bucketEnd;

        public VariantSpec(String bucketId, int bucketStart, int bucketEnd) {
            this.bucketId = bucketId;
            this.bucketStart = bucketStart;
            this.bucketEnd = bucketEnd;
        }

        public String getBucketId() { return bucketId; }
        public int getBucketStart() { return bucketStart; }
        public int getBucketEnd() { return bucketEnd; }
    }
}