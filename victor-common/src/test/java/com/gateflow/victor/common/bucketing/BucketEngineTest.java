package com.gateflow.victor.common.bucketing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BucketEngine 单元测试 — 涵盖分桶计算、变体匹配、跨平台一致性。
 */
class BucketEngineTest {

    // ========== computeBucket ==========

    @Test
    void shouldComputeDeterministicBucket() {
        int b1 = BucketEngine.computeBucket("user_123", "layer_001", "v1");
        int b2 = BucketEngine.computeBucket("user_123", "layer_001", "v1");
        assertEquals(b1, b2, "相同输入应产生相同桶号");
    }

    @Test
    void shouldComputeBucketInRange() {
        for (int i = 0; i < 1000; i++) {
            int b = BucketEngine.computeBucket("user_" + i, "layer_test", "salt");
            assertTrue(b >= 0 && b < 10000, "桶号应在 0-9999 范围内: " + b);
        }
    }

    @Test
    void shouldProduceDifferentBucketsForDifferentUsers() {
        int b1 = BucketEngine.computeBucket("user_001", "layer_001", "v1");
        int b2 = BucketEngine.computeBucket("user_002", "layer_001", "v1");
        // 极大概率不同（碰撞概率 < 1/10000）
        assertNotEquals(b1, b2, "不同用户通常产生不同桶号");
    }

    @Test
    void shouldProduceDifferentBucketsForDifferentLayers() {
        int b1 = BucketEngine.computeBucket("user_123", "layer_001", "v1");
        int b2 = BucketEngine.computeBucket("user_123", "layer_002", "v1");
        assertNotEquals(b1, b2, "不同层应产生不同桶号（正交分层）");
    }

    @Test
    void shouldProduceDifferentBucketsForDifferentSalt() {
        int b1 = BucketEngine.computeBucket("user_123", "layer_001", "salt_a");
        int b2 = BucketEngine.computeBucket("user_123", "layer_001", "salt_b");
        assertNotEquals(b1, b2, "不同盐值应产生不同桶号");
    }

    // ========== 跨平台一致性测试向量 ==========

    @Test
    void crossPlatformVector_user123_layer001_saltV1() {
        int bucket = BucketEngine.computeBucket("user_123", "layer_001", "v1");
        assertEquals(473, bucket, "跨平台一致性：Java/Swift/Kotlin/TypeScript 必须一致");
    }

    @Test
    void crossPlatformVector_user456_layerRecommend_salt2024() {
        int bucket = BucketEngine.computeBucket("user_456", "layer_recommend", "salt_2024");
        assertEquals(2896, bucket, "跨平台一致性测试向量");
    }

    @Test
    void crossPlatformVector_user789_layerSearch_saltProd() {
        int bucket = BucketEngine.computeBucket("user_789", "layer_search", "salt_prod");
        assertEquals(1511, bucket, "跨平台一致性测试向量");
    }

    @Test
    void crossPlatformVector_alice_layerDefault_saltDefault() {
        int bucket = BucketEngine.computeBucket("alice", "default", "victor_default");
        assertEquals(7176, bucket, "跨平台一致性测试向量");
    }

    // ========== findBucket ==========

    @Test
    void shouldFindMatchingVariant() {
        List<BucketEngine.BucketSpec> specs = List.of(
                new BucketEngine.BucketSpec("control", 0, 4999, null),
                new BucketEngine.BucketSpec("treatment", 5000, 7499, null),
                new BucketEngine.BucketSpec("treatment_b", 7500, 9999, null)
        );

        assertEquals("control", BucketEngine.findBucket(0, specs));
        assertEquals("control", BucketEngine.findBucket(4999, specs));
        assertEquals("treatment", BucketEngine.findBucket(5000, specs));
        assertEquals("treatment", BucketEngine.findBucket(7499, specs));
        assertEquals("treatment_b", BucketEngine.findBucket(9999, specs));
    }

    @Test
    void shouldReturnNullForUnmatchedBucket() {
        List<BucketEngine.BucketSpec> specs = List.of(
                new BucketEngine.BucketSpec("control", 0, 4999, null)
        );
        assertNull(BucketEngine.findBucket(5000, specs));
    }

    @Test
    void shouldReturnNullForEmptySpecs() {
        assertNull(BucketEngine.findBucket(5000, null));
        assertNull(BucketEngine.findBucket(5000, List.of()));
    }

    // ========== isInExperiment ==========

    @Test
    void shouldDetectInExperiment() {
        BucketEngine.ExperimentSpec exp = new BucketEngine.ExperimentSpec(
                "exp_001", "layer_001", "salt", 0, 4999, List.of());
        assertTrue(BucketEngine.isInExperiment(0, exp));
        assertTrue(BucketEngine.isInExperiment(4999, exp));
        assertFalse(BucketEngine.isInExperiment(5000, exp));
    }

    // ========== computeBucketResult ==========

    @Test
    void shouldReturnHitResult() {
        BucketEngine.BucketSpec spec = new BucketEngine.BucketSpec("treatment", 0, 9999, "{\"color\":\"red\"}");
        BucketEngine.ExperimentSpec exp = new BucketEngine.ExperimentSpec(
                "exp_001", "layer_001", "salt", 0, 9999, List.of(spec));

        BucketResult result = BucketEngine.computeBucketResult("user_123", exp);
        assertTrue(result.isHit());
        assertNotNull(result.getBucketOrNull());
        assertEquals("exp_001", result.getExperimentKey());
    }

    // ========== computeAllBucketResults ==========

    @Test
    void shouldComputeAllResults() {
        BucketEngine.BucketSpec spec1 = new BucketEngine.BucketSpec("control", 0, 4999, null);
        BucketEngine.BucketSpec spec2 = new BucketEngine.BucketSpec("treatment", 0, 9999, null);

        BucketEngine.ExperimentSpec exp1 = new BucketEngine.ExperimentSpec(
                "exp_001", "layer_001", "s1", 0, 9999, List.of(spec1));
        BucketEngine.ExperimentSpec exp2 = new BucketEngine.ExperimentSpec(
                "exp_002", "layer_002", "s2", 0, 9999, List.of(spec2));

        List<BucketResult> results = BucketEngine.computeAllBucketResults("user_123", List.of(exp1, exp2));
        assertEquals(2, results.size());
    }

    // ========== 边界条件 ==========

    @Test
    void shouldHandleEmptyUserId() {
        int bucket = BucketEngine.computeBucket("", "layer_001", "salt");
        assertTrue(bucket >= 0 && bucket < 10000);
    }

    @Test
    void shouldHandleUnicodeUserId() {
        int bucket = BucketEngine.computeBucket("用户_中文_日本語", "layer_001", "salt");
        assertTrue(bucket >= 0 && bucket < 10000);
    }

    @Test
    void shouldDistributeEvenly() {
        // 验证桶分布均匀性：10000 个用户应覆盖大部分桶
        int[] buckets = new int[10000];
        for (int i = 0; i < 10000; i++) {
            int b = BucketEngine.computeBucket("user_" + i, "layer_001", "salt");
            buckets[b]++;
        }
        int emptyBuckets = 0;
        for (int count : buckets) {
            if (count == 0) emptyBuckets++;
        }
        // 10000 个用户映射到 10000 个桶，空桶比例应低于 40%
        assertTrue(emptyBuckets < 4000, "空桶数=" + emptyBuckets + "，分布应大致均匀");
    }
}
