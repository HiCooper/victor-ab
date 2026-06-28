package com.gateflow.victor.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.domain.dto.ConfigResponse;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.infra.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConfigService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private ExperimentMapper experimentMapper;

    @Mock
    private LayerMapper layerMapper;

    @Mock
    private BucketMapper bucketMapper;

    @Mock
    private DomainMapper domainMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ConfigService configService;

    private Experiment testExperiment;
    private Layer testLayer;
    private Bucket testBucket;

    @BeforeEach
    void setUp() {
        testLayer = new Layer();
        testLayer.setId(1L);
        testLayer.setLayerId("layer_ui");
        testLayer.setName("UI层");
        testLayer.setSalt("ui_salt");

        testExperiment = new Experiment();
        testExperiment.setId(1L);
        testExperiment.setExpId("exp_test_001");
        testExperiment.setName("测试实验");
        testExperiment.setLayerId(1L);
        testExperiment.setStatus("running");
        testExperiment.setBucketStart(0);
        testExperiment.setBucketEnd(9999);
        testExperiment.setUpdatedAt(LocalDateTime.of(2026, 6, 1, 10, 0, 0));

        testBucket = new Bucket();
        testBucket.setId(1L);
        testBucket.setExpId("exp_test_001");
        testBucket.setBucketId("control");
        testBucket.setBucketStart(0);
        testBucket.setBucketEnd(9999);
        testBucket.setParams("{\"color\":\"blue\"}");
    }

    /** 让 getLatestVersion 走「缓存未命中 → 抢到锁 → 计算」路径的公共桩 */
    private void stubRedisCacheMissWithLock() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    @DisplayName("获取完整配置 - 成功（批量查询 + 仅活跃分桶）")
    void getFullConfig_Success() {
        when(experimentMapper.selectRunningExperiments()).thenReturn(List.of(testExperiment));
        when(layerMapper.selectByIds(anyList())).thenReturn(List.of(testLayer));
        when(bucketMapper.selectActiveBucketsByExpIds(anyList())).thenReturn(List.of(testBucket));

        ConfigResponse response = configService.getFullConfig("server");

        assertNotNull(response);
        assertEquals("FULL", response.getChangeType());
        assertNotNull(response.getVersion());
        assertEquals(1, response.getExperiments().size());

        ConfigResponse.ExperimentConfig expConfig = response.getExperiments().get(0);
        assertEquals("exp_test_001", expConfig.getExpId());
        assertEquals("layer_ui", expConfig.getLayerId());
        assertEquals("ui_salt", expConfig.getSalt());
        assertEquals(1, expConfig.getBuckets().size());

        // 不再有 N+1：批量查询、活跃分桶
        verify(experimentMapper).selectRunningExperiments();
        verify(layerMapper).selectByIds(anyList());
        verify(bucketMapper).selectActiveBucketsByExpIds(anyList());
    }

    @Test
    @DisplayName("获取完整配置 - 无运行中的实验")
    void getFullConfig_NoRunningExperiments() {
        when(experimentMapper.selectRunningExperiments()).thenReturn(Collections.emptyList());

        ConfigResponse response = configService.getFullConfig("server");

        assertNotNull(response);
        assertEquals("FULL", response.getChangeType());
        assertEquals("v0-empty", response.getVersion());
        assertTrue(response.getExperiments().isEmpty());

        verify(experimentMapper).selectRunningExperiments();
    }

    @Test
    @DisplayName("获取完整配置 - 层不存在")
    void getFullConfig_LayerNotFound() {
        when(experimentMapper.selectRunningExperiments()).thenReturn(List.of(testExperiment));
        when(layerMapper.selectByIds(anyList())).thenReturn(Collections.emptyList());
        when(bucketMapper.selectActiveBucketsByExpIds(anyList())).thenReturn(List.of(testBucket));

        ConfigResponse response = configService.getFullConfig("server");

        assertNotNull(response);
        assertEquals(1, response.getExperiments().size());

        ConfigResponse.ExperimentConfig expConfig = response.getExperiments().get(0);
        assertNull(expConfig.getLayerId());
        assertNull(expConfig.getSalt());
    }

    @Test
    @DisplayName("获取最新版本 - 命中 Redis 缓存")
    void getLatestVersion_FromRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("v-cached");

        ConfigService.VersionInfo versionInfo = configService.getLatestVersion();

        assertNotNull(versionInfo);
        assertEquals("v-cached", versionInfo.getVersion());
        verify(experimentMapper, never()).selectRunningExperiments();
    }

    @Test
    @DisplayName("获取最新版本 - 缓存未命中时由运行中配置派生并回填缓存")
    void getLatestVersion_ComputesFromRunningConfig() {
        stubRedisCacheMissWithLock();
        when(experimentMapper.selectRunningExperiments()).thenReturn(List.of(testExperiment));

        ConfigService.VersionInfo versionInfo = configService.getLatestVersion();

        assertNotNull(versionInfo);
        assertTrue(versionInfo.getVersion().startsWith("v"));
        // 回填缓存
        verify(valueOperations).set(anyString(), eq(versionInfo.getVersion()), any(Duration.class));
    }

    @Test
    @DisplayName("版本一致性 - /config/version 与 /config/fetch 返回相同版本")
    void version_isConsistentBetweenLatestAndFullConfig() {
        // getLatestVersion 走 Redis 计算路径
        stubRedisCacheMissWithLock();
        when(experimentMapper.selectRunningExperiments()).thenReturn(List.of(testExperiment));
        when(layerMapper.selectByIds(anyList())).thenReturn(List.of(testLayer));
        when(bucketMapper.selectActiveBucketsByExpIds(anyList())).thenReturn(List.of(testBucket));

        String fromVersionEndpoint = configService.getLatestVersion().getVersion();
        String fromFetchEndpoint = configService.getFullConfig("server").getVersion();

        // 关键：两端版本必须一致，否则 SDK 会每次轮询都误判“有更新”而全量重拉
        assertEquals(fromVersionEndpoint, fromFetchEndpoint);
    }

    @Test
    @DisplayName("hasUpdate - 仅在配置版本变化时返回 true")
    void hasUpdate_detectsRealChange() {
        stubRedisCacheMissWithLock();
        when(experimentMapper.selectRunningExperiments()).thenReturn(List.of(testExperiment));

        String current = configService.getLatestVersion().getVersion();

        assertFalse(configService.hasUpdate(current), "相同版本不应判定为有更新");
        assertTrue(configService.hasUpdate("some-stale-version"), "不同版本应判定为有更新");
    }

    @Test
    @DisplayName("版本随配置内容变化 - updatedAt 改变则版本改变")
    void version_changesWhenConfigChanges() {
        stubRedisCacheMissWithLock();

        when(experimentMapper.selectRunningExperiments()).thenReturn(List.of(testExperiment));
        String v1 = configService.getLatestVersion().getVersion();

        Experiment edited = new Experiment();
        edited.setExpId("exp_test_001");
        edited.setUpdatedAt(testExperiment.getUpdatedAt().plusSeconds(1));
        when(experimentMapper.selectRunningExperiments()).thenReturn(List.of(edited));
        String v2 = configService.getLatestVersion().getVersion();

        assertNotEquals(v1, v2);
    }

    @Test
    @DisplayName("获取增量配置 - 已弃用，降级为全量拉取")
    void getIncrementalConfig_FallsBackToFullPull() {
        // 增量模式已弃用：getIncrementalConfig 直接返回全量配置（changeType=FULL）
        when(experimentMapper.selectRunningExperiments()).thenReturn(Collections.emptyList());

        ConfigResponse response = configService.getIncrementalConfig("20240505-120000", "server");

        assertNotNull(response);
        assertEquals("FULL", response.getChangeType());
        assertTrue(response.getExperiments().isEmpty());
    }
}
