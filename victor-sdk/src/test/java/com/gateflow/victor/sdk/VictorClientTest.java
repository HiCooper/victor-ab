package com.gateflow.victor.sdk;

import com.gateflow.victor.sdk.model.SdkConfigResponse;
import com.gateflow.victor.sdk.model.SdkExperimentTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VictorClient SDK 测试
 * <p>
 * 测试分桶计算、参数获取等核心逻辑
 * 注意：系统使用10000个桶（0-9999），支持0.1%粒度流量分配
 */
class VictorClientTest {

    private VictorClient client;
    private VictorConfig config;

    @BeforeEach
    void setUp() {
        config = VictorConfig.builder()
                .serverUrl("http://localhost:8080")
                .platform("server")
                .pollingInterval(30)
                .cacheMaxSize(1000)
                .cacheExpiry(604800) // 7天
                .requestTimeout(5000)
                .build();
    }

    /**
     * 创建测试用的实验配置
     */
    private SdkConfigResponse.ExperimentConfig createTestExperimentConfig(
            String expId, String layerId, String salt,
            int bucketStart, int bucketEnd,
            List<SdkConfigResponse.BucketConfig> buckets) {
        SdkConfigResponse.ExperimentConfig config = new SdkConfigResponse.ExperimentConfig();
        config.setExpId(expId);
        config.setLayerId(layerId);
        config.setSalt(salt);
        config.setBucketStart(bucketStart);
        config.setBucketEnd(bucketEnd);
        config.setBuckets(buckets);
        return config;
    }

    /**
     * 创建测试用的版本配置
     */
    private SdkConfigResponse.BucketConfig createTestBucketConfig(
            String bucketKey, int bucketStart, int bucketEnd, Map<String, Object> params) {
        SdkConfigResponse.BucketConfig config = new SdkConfigResponse.BucketConfig();
        config.setBucketKey(bucketKey);
        config.setBucketStart(bucketStart);
        config.setBucketEnd(bucketEnd);
        config.setParams(params);
        return config;
    }

    @Test
    @DisplayName("VictorConfig - 默认值测试")
    void victorConfig_DefaultValues() {
        VictorConfig defaultConfig = VictorConfig.builder().build();

        assertEquals("http://localhost:8080", defaultConfig.getServerUrl());
        assertEquals("server", defaultConfig.getPlatform());
        assertEquals(30, defaultConfig.getPollingInterval());
        assertEquals(604800, defaultConfig.getCacheExpiry());
        assertEquals(1000, defaultConfig.getCacheMaxSize());
        assertEquals(5000, defaultConfig.getRequestTimeout());
        assertEquals(3, defaultConfig.getRetryCount());
        assertTrue(defaultConfig.isEventTrackingEnabled());
        assertEquals(100, defaultConfig.getEventBatchSize());
    }

    @Test
    @DisplayName("VictorConfig - 自定义值测试")
    void victorConfig_CustomValues() {
        VictorConfig customConfig = VictorConfig.builder()
                .serverUrl("http://custom.server:9000")
                .apiKey("test-api-key")
                .platform("web")
                .pollingInterval(60)
                .cacheExpiry(86400)
                .cacheMaxSize(500)
                .requestTimeout(10000)
                .retryCount(5)
                .eventTrackingEnabled(false)
                .eventBatchSize(50)
                .build();

        assertEquals("http://custom.server:9000", customConfig.getServerUrl());
        assertEquals("test-api-key", customConfig.getApiKey());
        assertEquals("web", customConfig.getPlatform());
        assertEquals(60, customConfig.getPollingInterval());
        assertEquals(86400, customConfig.getCacheExpiry());
        assertEquals(500, customConfig.getCacheMaxSize());
        assertEquals(10000, customConfig.getRequestTimeout());
        assertEquals(5, customConfig.getRetryCount());
        assertFalse(customConfig.isEventTrackingEnabled());
        assertEquals(50, customConfig.getEventBatchSize());
    }

    @Test
    @DisplayName("SdkConfigResponse - 数据结构测试")
    void sdkConfigResponse_DataStructure() {
        SdkConfigResponse response = new SdkConfigResponse();
        response.setVersion("20240505-120000");
        response.setEtag("abc123");
        response.setChangeType("FULL");

        List<SdkConfigResponse.BucketConfig> buckets = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("color", "blue");
        params.put("size", 10);
        buckets.add(createTestBucketConfig("control", 0, 4999, params));
        buckets.add(createTestBucketConfig("treatment", 5000, 9999, null));

        List<SdkConfigResponse.ExperimentConfig> experiments = new ArrayList<>();
        experiments.add(createTestExperimentConfig("exp_test", "layer_ui", "ui_salt", 0, 9999, buckets));

        response.setExperiments(experiments);
        response.setDeletedExperimentIds(List.of("exp_old_1", "exp_old_2"));

        // 验证数据结构
        assertEquals("20240505-120000", response.getVersion());
        assertEquals("abc123", response.getEtag());
        assertEquals("FULL", response.getChangeType());
        assertEquals(1, response.getExperiments().size());
        assertEquals(2, response.getDeletedExperimentIds().size());

        SdkConfigResponse.ExperimentConfig expConfig = response.getExperiments().get(0);
        assertEquals("exp_test", expConfig.getExpId());
        assertEquals("layer_ui", expConfig.getLayerId());
        assertEquals("ui_salt", expConfig.getSalt());
        assertEquals(0, expConfig.getBucketStart());
        assertEquals(9999, expConfig.getBucketEnd());
        assertEquals(2, expConfig.getBuckets().size());

        SdkConfigResponse.BucketConfig bucketConfig = expConfig.getBuckets().get(0);
        assertEquals("control", bucketConfig.getBucketKey());
        assertEquals(0, bucketConfig.getBucketStart());
        assertEquals(4999, bucketConfig.getBucketEnd());
        assertEquals(2, bucketConfig.getParams().size());
        assertEquals("blue", bucketConfig.getParams().get("color"));
        assertEquals(10, bucketConfig.getParams().get("size"));
    }

    @Test
    @DisplayName("SdkExperimentTag - 构造测试")
    void sdkExperimentTag_Construction() {
        SdkExperimentTag tag = new SdkExperimentTag();
        tag.setExpId("exp_test_001");
        tag.setBucket("treatment");
        tag.setLayer("layer_ui");

        assertEquals("exp_test_001", tag.getExpId());
        assertEquals("treatment", tag.getBucket());
        assertEquals("layer_ui", tag.getLayer());
    }

    @Test
    @DisplayName("分桶一致性测试 - 相同用户相同实验应返回相同版本")
    void bucketingConsistency_SameUserSameExperiment() {
        String userId = "user_12345";
        String expId = "exp_test";

        // 模拟100%流量实验配置：50/50分桶 (使用10000桶)
        List<SdkConfigResponse.BucketConfig> buckets = new ArrayList<>();
        buckets.add(createTestBucketConfig("control", 0, 4999, null));
        buckets.add(createTestBucketConfig("treatment", 5000, 9999, null));

        SdkConfigResponse.ExperimentConfig expConfig =
                createTestExperimentConfig(expId, "layer_ui", "test_salt", 0, 9999, buckets);

        // 使用BucketEngine直接计算验证一致性
        com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec spec =
                new com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec(
                        expConfig.getExpId(),
                        expConfig.getLayerId(),
                        expConfig.getSalt(),
                        expConfig.getBucketStart(),
                        expConfig.getBucketEnd(),
                        buckets.stream()
                                .map(v -> new com.gateflow.victor.common.bucketing.BucketEngine.BucketSpec(
                                        v.getBucketKey(), v.getBucketStart(), v.getBucketEnd(), ""))
                                .toList()
                );

        // 计算多次，结果应一致
        com.gateflow.victor.common.bucketing.BucketResult result1 =
                com.gateflow.victor.common.bucketing.BucketEngine.computeBucketResult(userId, spec);
        com.gateflow.victor.common.bucketing.BucketResult result2 =
                com.gateflow.victor.common.bucketing.BucketEngine.computeBucketResult(userId, spec);
        com.gateflow.victor.common.bucketing.BucketResult result3 =
                com.gateflow.victor.common.bucketing.BucketEngine.computeBucketResult(userId, spec);

        assertEquals(result1.getBucket(), result2.getBucket());
        assertEquals(result2.getBucket(), result3.getBucket());
        assertTrue(result1.isHit()); // 100%流量实验，必定命中
    }

    @Test
    @DisplayName("分桶分布测试 - 多用户应均匀分布到不同版本")
    void bucketingDistribution_MultipleUsers() {
        // 100%流量实验，50/50分配
        List<SdkConfigResponse.BucketConfig> buckets = new ArrayList<>();
        buckets.add(createTestBucketConfig("control", 0, 4999, null));
        buckets.add(createTestBucketConfig("treatment", 5000, 9999, null));

        SdkConfigResponse.ExperimentConfig expConfig =
                createTestExperimentConfig("exp_dist_test", "layer_test", "dist_salt", 0, 9999, buckets);

        com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec spec =
                new com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec(
                        expConfig.getExpId(),
                        expConfig.getLayerId(),
                        expConfig.getSalt(),
                        expConfig.getBucketStart(),
                        expConfig.getBucketEnd(),
                        buckets.stream()
                                .map(v -> new com.gateflow.victor.common.bucketing.BucketEngine.BucketSpec(
                                        v.getBucketKey(), v.getBucketStart(), v.getBucketEnd(), ""))
                                .toList()
                );

        // 使用多个用户测试分布
        int controlCount = 0;
        int treatmentCount = 0;
        int totalUsers = 1000;

        for (int i = 0; i < totalUsers; i++) {
            String userId = "user_" + i;
            com.gateflow.victor.common.bucketing.BucketResult result =
                    com.gateflow.victor.common.bucketing.BucketEngine.computeBucketResult(userId, spec);
            if (result.isHit()) {
                if ("control".equals(result.getBucket())) {
                    controlCount++;
                } else if ("treatment".equals(result.getBucket())) {
                    treatmentCount++;
                }
            }
        }

        // 100%流量，所有用户都命中
        assertEquals(totalUsers, controlCount + treatmentCount);

        // 验证分布大致均匀 (允许±10%误差)
        int expectedPerBucket = totalUsers / 2;
        double tolerance = 0.1 * expectedPerBucket;

        assertTrue(Math.abs(controlCount - expectedPerBucket) <= tolerance,
                "Control count " + controlCount + " should be close to " + expectedPerBucket);
        assertTrue(Math.abs(treatmentCount - expectedPerBucket) <= tolerance,
                "Treatment count " + treatmentCount + " should be close to " + expectedPerBucket);
    }

    @Test
    @DisplayName("流量分配测试 - 10%流量实验")
    void trafficAllocation_TenPercent() {
        // 10%流量实验 (桶范围0-999)
        List<SdkConfigResponse.BucketConfig> buckets = new ArrayList<>();
        buckets.add(createTestBucketConfig("control", 0, 499, null));
        buckets.add(createTestBucketConfig("treatment", 500, 999, null));

        SdkConfigResponse.ExperimentConfig expConfig =
                createTestExperimentConfig("exp_10pct", "layer_test", "salt_10pct", 0, 999, buckets);

        com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec spec =
                new com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec(
                        expConfig.getExpId(),
                        expConfig.getLayerId(),
                        expConfig.getSalt(),
                        expConfig.getBucketStart(),
                        expConfig.getBucketEnd(),
                        buckets.stream()
                                .map(v -> new com.gateflow.victor.common.bucketing.BucketEngine.BucketSpec(
                                        v.getBucketKey(), v.getBucketStart(), v.getBucketEnd(), ""))
                                .toList()
                );

        int hitCount = 0;
        int totalUsers = 1000;

        for (int i = 0; i < totalUsers; i++) {
            String userId = "user_" + i;
            com.gateflow.victor.common.bucketing.BucketResult result =
                    com.gateflow.victor.common.bucketing.BucketEngine.computeBucketResult(userId, spec);
            if (result.isHit()) {
                hitCount++;
            }
        }

        // 大约10%用户命中
        int expectedHits = totalUsers / 10;
        double tolerance = 0.2 * expectedHits; // 允许20%误差

        assertTrue(Math.abs(hitCount - expectedHits) <= tolerance,
                "Hit count " + hitCount + " should be close to " + expectedHits);
    }

    @Test
    @DisplayName("层内实验互斥测试 - 同一层内不同实验的用户分桶应互斥")
    void layerMutualExclusion_SameLayerDifferentExperiments() {
        String layerId = "layer_ui";
        String salt = "ui_salt"; // 相同盐值保证互斥

        // 实验1: 桶范围 0-4999 (50%流量)
        List<SdkConfigResponse.BucketConfig> buckets1 = new ArrayList<>();
        buckets1.add(createTestBucketConfig("exp1_control", 0, 2499, null));
        buckets1.add(createTestBucketConfig("exp1_treatment", 2500, 4999, null));

        SdkConfigResponse.ExperimentConfig expConfig1 =
                createTestExperimentConfig("exp_1", layerId, salt, 0, 4999, buckets1);

        // 实验2: 桶范围 5000-9999 (50%流量，同一层，不重叠)
        List<SdkConfigResponse.BucketConfig> buckets2 = new ArrayList<>();
        buckets2.add(createTestBucketConfig("exp2_control", 5000, 7499, null));
        buckets2.add(createTestBucketConfig("exp2_treatment", 7500, 9999, null));

        SdkConfigResponse.ExperimentConfig expConfig2 =
                createTestExperimentConfig("exp_2", layerId, salt, 5000, 9999, buckets2);

        com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec spec1 =
                new com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec(
                        expConfig1.getExpId(),
                        expConfig1.getLayerId(),
                        expConfig1.getSalt(),
                        expConfig1.getBucketStart(),
                        expConfig1.getBucketEnd(),
                        buckets1.stream()
                                .map(v -> new com.gateflow.victor.common.bucketing.BucketEngine.BucketSpec(
                                        v.getBucketKey(), v.getBucketStart(), v.getBucketEnd(), ""))
                                .toList()
                );

        com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec spec2 =
                new com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec(
                        expConfig2.getExpId(),
                        expConfig2.getLayerId(),
                        expConfig2.getSalt(),
                        expConfig2.getBucketStart(),
                        expConfig2.getBucketEnd(),
                        buckets2.stream()
                                .map(v -> new com.gateflow.victor.common.bucketing.BucketEngine.BucketSpec(
                                        v.getBucketKey(), v.getBucketStart(), v.getBucketEnd(), ""))
                                .toList()
                );

        // 验证同一用户要么命中实验1，要么命中实验2，不会同时命中
        for (int i = 0; i < 100; i++) {
            String userId = "user_" + i;
            com.gateflow.victor.common.bucketing.BucketResult result1 =
                    com.gateflow.victor.common.bucketing.BucketEngine.computeBucketResult(userId, spec1);
            com.gateflow.victor.common.bucketing.BucketResult result2 =
                    com.gateflow.victor.common.bucketing.BucketEngine.computeBucketResult(userId, spec2);

            // 两个实验不能同时命中（同一层，不同桶范围）
            assertFalse(result1.isHit() && result2.isHit(),
                    "User " + userId + " should not hit both experiments in same layer");
        }
    }

    @Test
    @DisplayName("不同层实验不互斥测试 - 不同层的用户可同时命中多个实验")
    void differentLayerNonMutualExclusion() {
        // 实验1: 层1，100%流量
        List<SdkConfigResponse.BucketConfig> buckets1 = new ArrayList<>();
        buckets1.add(createTestBucketConfig("layer1_control", 0, 4999, null));
        buckets1.add(createTestBucketConfig("layer1_treatment", 5000, 9999, null));

        SdkConfigResponse.ExperimentConfig expConfig1 =
                createTestExperimentConfig("exp_layer1", "layer_1", "salt_1", 0, 9999, buckets1);

        // 实验2: 层2，100%流量 (不同层，不同盐值)
        List<SdkConfigResponse.BucketConfig> buckets2 = new ArrayList<>();
        buckets2.add(createTestBucketConfig("layer2_control", 0, 4999, null));
        buckets2.add(createTestBucketConfig("layer2_treatment", 5000, 9999, null));

        SdkConfigResponse.ExperimentConfig expConfig2 =
                createTestExperimentConfig("exp_layer2", "layer_2", "salt_2", 0, 9999, buckets2);

        com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec spec1 =
                new com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec(
                        expConfig1.getExpId(),
                        expConfig1.getLayerId(),
                        expConfig1.getSalt(),
                        expConfig1.getBucketStart(),
                        expConfig1.getBucketEnd(),
                        buckets1.stream()
                                .map(v -> new com.gateflow.victor.common.bucketing.BucketEngine.BucketSpec(
                                        v.getBucketKey(), v.getBucketStart(), v.getBucketEnd(), ""))
                                .toList()
                );

        com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec spec2 =
                new com.gateflow.victor.common.bucketing.BucketEngine.ExperimentSpec(
                        expConfig2.getExpId(),
                        expConfig2.getLayerId(),
                        expConfig2.getSalt(),
                        expConfig2.getBucketStart(),
                        expConfig2.getBucketEnd(),
                        buckets2.stream()
                                .map(v -> new com.gateflow.victor.common.bucketing.BucketEngine.BucketSpec(
                                        v.getBucketKey(), v.getBucketStart(), v.getBucketEnd(), ""))
                                .toList()
                );

        // 验证不同层的实验可以同时命中
        int bothHitCount = 0;
        for (int i = 0; i < 100; i++) {
            String userId = "user_" + i;
            com.gateflow.victor.common.bucketing.BucketResult result1 =
                    com.gateflow.victor.common.bucketing.BucketEngine.computeBucketResult(userId, spec1);
            com.gateflow.victor.common.bucketing.BucketResult result2 =
                    com.gateflow.victor.common.bucketing.BucketEngine.computeBucketResult(userId, spec2);

            // 两个实验都是100%流量，应该都命中
            assertTrue(result1.isHit());
            assertTrue(result2.isHit());
            bothHitCount++;
        }

        // 所有用户都应该同时命中两个实验（不同层，100%流量）
        assertEquals(100, bothHitCount);
    }

    @Test
    @DisplayName("BucketConfig参数测试 - 空参数处理")
    void bucketConfig_NullParams() {
        SdkConfigResponse.BucketConfig config = createTestBucketConfig("test_bucket", 0, 9999, null);

        assertNull(config.getParams());
        assertEquals("test_bucket", config.getBucketKey());
        assertEquals(0, config.getBucketStart());
        assertEquals(9999, config.getBucketEnd());
    }

    @Test
    @DisplayName("ExperimentConfig测试 - 空版本列表")
    void experimentConfig_EmptyBuckets() {
        SdkConfigResponse.ExperimentConfig config =
                createTestExperimentConfig("exp_test", "layer_test", "salt_test", 0, 9999, new ArrayList<>());

        assertEquals("exp_test", config.getExpId());
        assertTrue(config.getBuckets().isEmpty());
    }

    @Test
    @DisplayName("分桶确定性测试 - 相同输入必定产生相同输出")
    void bucketingDeterminism_SameInputSameOutput() {
        String userId = "deterministic_user";
        String layerId = "deterministic_layer";
        String salt = "deterministic_salt";

        // 计算100次，桶号应完全一致
        int bucket1 = com.gateflow.victor.common.bucketing.BucketEngine.computeBucket(userId, layerId, salt);
        int bucket2 = com.gateflow.victor.common.bucketing.BucketEngine.computeBucket(userId, layerId, salt);
        int bucket3 = com.gateflow.victor.common.bucketing.BucketEngine.computeBucket(userId, layerId, salt);

        assertEquals(bucket1, bucket2);
        assertEquals(bucket2, bucket3);
        assertTrue(bucket1 >= 0 && bucket1 < 10000);
    }

    @Test
    @DisplayName("分桶散列测试 - 不同用户应产生不同桶号")
    void bucketingScatter_DifferentUsersDifferentBuckets() {
        String layerId = "scatter_layer";
        String salt = "scatter_salt";

        Set<Integer> buckets = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String userId = "scatter_user_" + i;
            int bucket = com.gateflow.victor.common.bucketing.BucketEngine.computeBucket(userId, layerId, salt);
            buckets.add(bucket);
        }

        // 1000个不同用户应产生大量不同的桶号
        assertTrue(buckets.size() > 500, "Different users should scatter across many buckets");
    }
}