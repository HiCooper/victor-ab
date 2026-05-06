package com.gateflow.victor.bucketing;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BucketEngine 单元测试
 */
class BucketEngineTest {

    @Test
    void testComputeBucketConsistency() {
        // 相同输入应产生相同桶号
        String userId = "user_123";
        String layerId = "layer_recommend";
        String salt = "victor_salt";

        int bucket1 = BucketEngine.computeBucket(userId, layerId, salt);
        int bucket2 = BucketEngine.computeBucket(userId, layerId, salt);

        assertEquals(bucket1, bucket2, "Same input should produce same bucket");
    }

    @Test
    void testComputeBucketRange() {
        // 桶号应在有效范围内 (0-9999)
        for (int i = 0; i < 1000; i++) {
            String userId = "user_" + i;
            int bucket = BucketEngine.computeBucket(userId, "layer_test", "salt");
            assertTrue(bucket >= 0 && bucket < 10000,
                    "Bucket should be in range [0, 9999]");
        }
    }

    @Test
    void testComputeBucketDistribution() {
        // 测试桶号分布均匀性
        int[] bucketCounts = new int[100];

        for (int i = 0; i < 10000; i++) {
            String userId = "user_" + i;
            int bucket = BucketEngine.computeBucket(userId, "layer_test", "salt");
            int bucketIndex = bucket / 100; // 分成100个大桶
            bucketCounts[bucketIndex]++;
        }

        // 每个大桶大约100次，允许±50的偏差（考虑统计波动）
        for (int count : bucketCounts) {
            assertTrue(count > 50 && count < 150,
                    "Bucket distribution should be approximately uniform");
        }
    }

    @Test
    void testDifferentLayersOrthogonal() {
        // 不同层应该产生不同的桶号 (正交性)
        String userId = "user_123";
        String salt1 = "layer_ui_salt";
        String salt2 = "layer_recommend_salt";

        int bucket1 = BucketEngine.computeBucket(userId, "layer_ui", salt1);
        int bucket2 = BucketEngine.computeBucket(userId, "layer_recommend", salt2);

        // 不同层大概率产生不同桶号
        assertNotEquals(bucket1, bucket2, 
                "Different layers should produce different buckets (orthogonal)");
    }

    @Test
    void testFindVariant() {
        // 创建版本规格
        List<BucketEngine.VariantSpec> variants = Arrays.asList(
                new BucketEngine.VariantSpec("control", 0, 4999),
                new BucketEngine.VariantSpec("treatment_a", 5000, 9999)
        );

        // 测试版本查找
        assertEquals("control", BucketEngine.findVariant(0, variants));
        assertEquals("control", BucketEngine.findVariant(2500, variants));
        assertEquals("control", BucketEngine.findVariant(4999, variants));
        assertEquals("treatment_a", BucketEngine.findVariant(5000, variants));
        assertEquals("treatment_a", BucketEngine.findVariant(7500, variants));
        assertEquals("treatment_a", BucketEngine.findVariant(9999, variants));
    }

    @Test
    void testFindVariantOutOfRange() {
        // 桶号超出实验范围应返回null
        List<BucketEngine.VariantSpec> variants = Arrays.asList(
                new BucketEngine.VariantSpec("control", 0, 999)
        );

        assertNull(BucketEngine.findVariant(1000, variants), 
                "Out of range bucket should return null");
    }

    @Test
    void testIsInExperiment() {
        // 创建实验规格
        BucketEngine.ExperimentSpec experiment = new BucketEngine.ExperimentSpec(
                "exp_test", "layer_test", "salt",
                0, 999, // 实验占用前1000个桶
                Arrays.asList(
                        new BucketEngine.VariantSpec("control", 0, 499),
                        new BucketEngine.VariantSpec("treatment", 500, 999)
                )
        );

        // 测试是否在实验范围内
        assertTrue(BucketEngine.isInExperiment(0, experiment));
        assertTrue(BucketEngine.isInExperiment(500, experiment));
        assertTrue(BucketEngine.isInExperiment(999, experiment));
        assertFalse(BucketEngine.isInExperiment(1000, experiment));
        assertFalse(BucketEngine.isInExperiment(5000, experiment));
    }

    @Test
    void testComputeBucketResult() {
        // 创建实验规格
        BucketEngine.ExperimentSpec experiment = new BucketEngine.ExperimentSpec(
                "exp_button_color", "layer_ui", "salt_ui",
                0, 999, // 10%流量
                Arrays.asList(
                        new BucketEngine.VariantSpec("control", 0, 499),
                        new BucketEngine.VariantSpec("blue", 500, 999)
                )
        );

        String userId = "user_123";
        BucketResult result = BucketEngine.computeBucketResult(userId, experiment);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("exp_button_color", result.getExperimentKey());
        assertNotNull(result.getBucket());
    }

    @Test
    void testComputeAllBucketResults() {
        // 创建多个实验规格
        List<BucketEngine.ExperimentSpec> experiments = Arrays.asList(
                new BucketEngine.ExperimentSpec(
                        "exp_1", "layer_1", "salt_1", 0, 999,
                        Arrays.asList(new BucketEngine.VariantSpec("control", 0, 999))
                ),
                new BucketEngine.ExperimentSpec(
                        "exp_2", "layer_2", "salt_2", 1000, 1999,
                        Arrays.asList(new BucketEngine.VariantSpec("treatment", 1000, 1999))
                )
        );

        String userId = "user_123";
        List<BucketResult> results = BucketEngine.computeAllBucketResults(userId, experiments);

        assertEquals(2, results.size());
        assertEquals("exp_1", results.get(0).getExperimentKey());
        assertEquals("exp_2", results.get(1).getExperimentKey());
    }

    @Test
    void testBucketStabilityOverTime() {
        // 用户分桶结果应该稳定，不随时间变化
        String userId = "stable_user";
        String layerId = "stable_layer";
        String salt = "stable_salt";

        // 多次计算应该结果一致
        int expectedBucket = BucketEngine.computeBucket(userId, layerId, salt);

        for (int i = 0; i < 100; i++) {
            int actualBucket = BucketEngine.computeBucket(userId, layerId, salt);
            assertEquals(expectedBucket, actualBucket,
                    "Bucket assignment should be stable over time");
        }
    }

    @Test
    void testVariantDistribution() {
        // 测试版本分配比例
        List<BucketEngine.VariantSpec> variants = Arrays.asList(
                new BucketEngine.VariantSpec("control", 0, 4999),    // 50%
                new BucketEngine.VariantSpec("treatment_a", 5000, 9999) // 50%
        );

        Map<String, Integer> variantCounts = new java.util.HashMap<>();
        variantCounts.put("control", 0);
        variantCounts.put("treatment_a", 0);

        // 模拟10000个用户分桶
        for (int i = 0; i < 10000; i++) {
            String userId = "user_" + i;
            int bucket = BucketEngine.computeBucket(userId, "layer_test", "salt");
            String variant = BucketEngine.findVariant(bucket, variants);
            if (variant != null) {
                variantCounts.merge(variant, 1, Integer::sum);
            }
        }

        // 版本比例应接近50% (允许±5%偏差)
        int controlCount = variantCounts.get("control");
        int treatmentCount = variantCounts.get("treatment_a");

        assertTrue(controlCount > 4500 && controlCount < 5500,
                "Control variant ratio should be ~50%");
        assertTrue(treatmentCount > 4500 && treatmentCount < 5500,
                "Treatment variant ratio should be ~50%");
    }
}