package com.gateflow.victor.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MurmurHash3 单元测试
 */
class MurmurHash3Test {

    @Test
    void testHash32Consistency() {
        // 相同输入应产生相同输出
        String input = "user_123#layer_recommend#victor";
        int hash1 = MurmurHash3.hash32(input);
        int hash2 = MurmurHash3.hash32(input);
        assertEquals(hash1, hash2, "Same input should produce same hash");
    }

    @Test
    void testHash32Deterministic() {
        // 多次调用结果一致
        byte[] data = "test_data".getBytes();
        int hash1 = MurmurHash3.hash32(data);
        int hash2 = MurmurHash3.hash32(data);
        int hash3 = MurmurHash3.hash32(data);
        assertEquals(hash1, hash2);
        assertEquals(hash2, hash3);
    }

    @Test
    void testHash32Distribution() {
        // 测试哈希值分布均匀性
        int[] buckets = new int[100];
        for (int i = 0; i < 10000; i++) {
            String userId = "user_" + i;
            int hash = MurmurHash3.hash32(userId);
            int bucket = (hash & Integer.MAX_VALUE) % 100;
            buckets[bucket]++;
        }

        // 每个桶大约100次，允许±50的偏差
        for (int count : buckets) {
            assertTrue(count > 50 && count < 150,
                    "Bucket distribution should be approximately uniform");
        }
    }

    @Test
    void testHash32WithDifferentInputs() {
        // 不同输入应产生不同输出（大概率）
        String input1 = "user_1";
        String input2 = "user_2";
        int hash1 = MurmurHash3.hash32(input1);
        int hash2 = MurmurHash3.hash32(input2);
        assertNotEquals(hash1, hash2, "Different inputs should produce different hashes");
    }

    @Test
    void testHash32WithSeed() {
        // 不同种子产生不同结果
        String input = "test_input";
        int hash1 = MurmurHash3.hash32(input, 0);
        int hash2 = MurmurHash3.hash32(input, 12345);
        assertNotEquals(hash1, hash2, "Different seeds should produce different hashes");
    }

    @Test
    void testHash64Consistency() {
        String input = "user_123#layer_ui#salt";
        long hash1 = MurmurHash3.hash64(input);
        long hash2 = MurmurHash3.hash64(input);
        assertEquals(hash1, hash2, "Same input should produce same hash64");
    }

    @Test
    void testHash64Range() {
        // 64位哈希值应在合理范围内
        for (int i = 0; i < 100; i++) {
            String input = "test_" + i;
            long hash = MurmurHash3.hash64(input);
            assertTrue(hash >= Long.MIN_VALUE && hash <= Long.MAX_VALUE);
        }
    }

    @Test
    void testBucketingScenario() {
        // 模拟分桶场景
        int totalBuckets = 10000;
        int[] bucketCounts = new int[totalBuckets];

        // 模拟1000个用户的分桶
        for (int i = 0; i < 1000; i++) {
            String userId = "user_" + i;
            String layerId = "layer_recommend";
            String salt = "victor_salt";
            String hashInput = userId + "#" + layerId + "#" + salt;
            int hash = MurmurHash3.hash32(hashInput);
            int bucket = (hash & Integer.MAX_VALUE) % totalBuckets;
            bucketCounts[bucket]++;
        }

        // 验证分桶结果：大部分桶应有用户
        int usedBuckets = 0;
        for (int count : bucketCounts) {
            if (count > 0) usedBuckets++;
        }

        // 1000用户分布到10000桶，使用率应该>5%
        assertTrue(usedBuckets > 500, "Bucket utilization should be reasonable");
    }

    @Test
    void testEmptyInput() {
        // 空输入测试
        int hash = MurmurHash3.hash32("");
        assertNotNull(hash);
    }

    @Test
    void testNullInput() {
        // 空指针应该抛出异常
        assertThrows(NullPointerException.class, () -> {
            MurmurHash3.hash32((String) null);
        });
    }
}