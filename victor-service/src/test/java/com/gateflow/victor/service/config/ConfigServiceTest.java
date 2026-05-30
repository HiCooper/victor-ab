package com.gateflow.victor.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.domain.dto.ConfigResponse;
import com.gateflow.victor.domain.entity.ConfigVersion;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Bucket;
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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ConfigVersionMapper configVersionMapper;

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
    private Variant testVariant;

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

        testVariant = new Bucket();
        testVariant.setId(1L);
        testVariant.setExpId("exp_test_001");
        testVariant.setBucketId("control");
        testVariant.setBucketStart(0);
        testVariant.setBucketEnd(499);
        testVariant.setParams("{\"color\":\"blue\"}");
    }

    @Test
    @DisplayName("获取完整配置 - 成功")
    void getFullConfig_Success() {
        when(experimentMapper.selectRunningExperiments()).thenReturn(List.of(testExperiment));
        when(layerMapper.selectById(1L)).thenReturn(testLayer);
        when(bucketMapper.selectByExpId("exp_test_001")).thenReturn(List.of(testVariant));

        ConfigResponse response = configService.getFullConfig("server");

        assertNotNull(response);
        assertEquals("FULL", response.getChangeType());
        assertNotNull(response.getVersion());
        assertEquals(1, response.getExperiments().size());

        ConfigResponse.ExperimentConfig expConfig = response.getExperiments().get(0);
        assertEquals("exp_test_001", expConfig.getExpId());
        assertEquals("layer_ui", expConfig.getLayerId());
        assertEquals("ui_salt", expConfig.getSalt());
        assertEquals(1, expConfig.getVariants().size());

        verify(experimentMapper).selectRunningExperiments();
        verify(layerMapper).selectById(1L);
        verify(bucketMapper).selectByExpId("exp_test_001");
    }

    @Test
    @DisplayName("获取完整配置 - 无运行中的实验")
    void getFullConfig_NoRunningExperiments() {
        when(experimentMapper.selectRunningExperiments()).thenReturn(Collections.emptyList());

        ConfigResponse response = configService.getFullConfig("server");

        assertNotNull(response);
        assertEquals("FULL", response.getChangeType());
        assertTrue(response.getExperiments().isEmpty());

        verify(experimentMapper).selectRunningExperiments();
    }

    @Test
    @DisplayName("获取完整配置 - 层不存在")
    void getFullConfig_LayerNotFound() {
        when(experimentMapper.selectRunningExperiments()).thenReturn(List.of(testExperiment));
        when(layerMapper.selectById(1L)).thenReturn(null);
        when(bucketMapper.selectByExpId("exp_test_001")).thenReturn(List.of(testVariant));

        ConfigResponse response = configService.getFullConfig("server");

        assertNotNull(response);
        assertEquals(1, response.getExperiments().size());

        ConfigResponse.ExperimentConfig expConfig = response.getExperiments().get(0);
        assertNull(expConfig.getLayerId());
        assertNull(expConfig.getSalt());
    }

    @Test
    @DisplayName("检查版本更新 - 有更新")
    void hasUpdate_True() {
        ConfigVersion version = new ConfigVersion();
        version.setVersion("20240505-120000");
        version.setCreatedAt(java.time.LocalDateTime.now());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(configVersionMapper.selectLatestVersion()).thenReturn(version);

        boolean hasUpdate = configService.hasUpdate("20240505-110000");

        assertTrue(hasUpdate);
        verify(configVersionMapper).selectLatestVersion();
    }

    @Test
    @DisplayName("检查版本更新 - 无更新")
    void hasUpdate_False() {
        ConfigVersion version = new ConfigVersion();
        version.setVersion("20240505-120000");
        version.setCreatedAt(java.time.LocalDateTime.now());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(configVersionMapper.selectLatestVersion()).thenReturn(version);

        boolean hasUpdate = configService.hasUpdate("20240505-120000");

        assertFalse(hasUpdate);
    }

    @Test
    @DisplayName("获取最新版本 - 从Redis")
    void getLatestVersion_FromRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("20240505-120000");

        ConfigService.VersionInfo versionInfo = configService.getLatestVersion();

        assertNotNull(versionInfo);
        assertEquals("20240505-120000", versionInfo.getVersion());
        verify(redisTemplate.opsForValue()).get(anyString());
        verify(configVersionMapper, never()).selectLatestVersion();
    }

    @Test
    @DisplayName("获取最新版本 - 从数据库")
    void getLatestVersion_FromDatabase() {
        ConfigVersion version = new ConfigVersion();
        version.setVersion("20240505-120000");
        version.setCreatedAt(java.time.LocalDateTime.now());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(configVersionMapper.selectLatestVersion()).thenReturn(version);

        ConfigService.VersionInfo versionInfo = configService.getLatestVersion();

        assertNotNull(versionInfo);
        assertEquals("20240505-120000", versionInfo.getVersion());
        verify(configVersionMapper).selectLatestVersion();
    }

    @Test
    @DisplayName("获取最新版本 - 无版本记录")
    void getLatestVersion_NoVersionRecord() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(configVersionMapper.selectLatestVersion()).thenReturn(null);

        ConfigService.VersionInfo versionInfo = configService.getLatestVersion();

        assertNotNull(versionInfo);
        assertNotNull(versionInfo.getVersion());
        verify(configVersionMapper).selectLatestVersion();
    }

    @Test
    @DisplayName("获取增量配置 - 无变更")
    void getIncrementalConfig_NoChanges() {
        when(configVersionMapper.selectChangesAfterVersion(anyString()))
                .thenReturn(Collections.emptyList());

        ConfigResponse response = configService.getIncrementalConfig("20240505-120000", "server");

        assertNotNull(response);
        assertEquals("20240505-120000", response.getVersion());
        assertEquals("INCREMENTAL", response.getChangeType());
        assertTrue(response.getExperiments().isEmpty());
    }
}
