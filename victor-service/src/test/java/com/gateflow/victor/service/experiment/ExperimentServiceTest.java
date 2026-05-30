package com.gateflow.victor.service.experiment;

import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.BucketMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ExperimentService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {

    @Mock
    private ExperimentMapper experimentMapper;

    @Mock
    private LayerMapper layerMapper;

    @Mock
    private BucketMapper bucketMapper;

    @Mock
    private ExperimentLifecycleService lifecycleService;

    @Mock
    private BucketVersionService versionService;

    @InjectMocks
    private ExperimentService experimentService;

    private Experiment testExperiment;
    private Layer testLayer;
    private Bucket testBucket;
    private Bucket testBucket2;

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
        testExperiment.setStatus("draft");

        // 两个bucket覆盖完整的实验桶范围 0-999 (共1000个桶)
        testBucket = new Bucket();
        testBucket.setId(1L);
        testBucket.setExpId("exp_test_001");
        testBucket.setBucketId("control");
        testBucket.setBucketStart(0);
        testBucket.setBucketEnd(4999);

        testBucket2 = new Bucket();
        testBucket2.setId(2L);
        testBucket2.setExpId("exp_test_001");
        testBucket2.setBucketId("treatment");
        testBucket2.setBucketStart(5000);
        testBucket2.setBucketEnd(9999);
    }

    @Test
    @DisplayName("创建实验 - 成功")
    void createExperiment_Success() {
        when(layerMapper.selectById(1L)).thenReturn(testLayer);
        when(experimentMapper.insert(any(Experiment.class))).thenReturn(1);
        when(bucketMapper.insert(any(Bucket.class))).thenReturn(1);
        when(versionService.generateVersion()).thenReturn("v1.0.0");

        List<Bucket> buckets = List.of(testBucket, testBucket2);
        Experiment created = experimentService.createExperiment(testExperiment, buckets);

        assertNotNull(created);
        assertEquals("draft", created.getStatus());
        verify(experimentMapper).insert(any(Experiment.class));
        verify(bucketMapper, times(2)).insert(any(Bucket.class));
    }

    @Test
    @DisplayName("创建实验 - 层不存在")
    void createExperiment_LayerNotFound() {
        when(layerMapper.selectById(1L)).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> experimentService.createExperiment(testExperiment, Collections.emptyList()));

        assertTrue(exception.getMessage().contains("层不存在"));
        verify(experimentMapper, never()).insert(any(Experiment.class));
    }

    @Test
    @DisplayName("创建实验 - 无版本")
    void createExperiment_NoBuckets() {
        when(layerMapper.selectById(1L)).thenReturn(testLayer);
        when(experimentMapper.insert(any(Experiment.class))).thenReturn(1);

        Experiment created = experimentService.createExperiment(testExperiment, null);

        assertNotNull(created);
        assertEquals("draft", created.getStatus());
        verify(bucketMapper, never()).insert(any(Bucket.class));
    }

    @Test
    @DisplayName("查询实验 - 成功")
    void getExperiment_Success() {
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);

        Experiment found = experimentService.getExperiment(1L);

        assertNotNull(found);
        assertEquals("exp_test_001", found.getExpId());
        verify(experimentMapper).selectById(1L);
    }

    @Test
    @DisplayName("查询实验 - 不存在")
    void getExperiment_NotFound() {
        when(experimentMapper.selectById(999L)).thenReturn(null);

        Experiment found = experimentService.getExperiment(999L);

        assertNull(found);
        verify(experimentMapper).selectById(999L);
    }

    @Test
    @DisplayName("根据业务标识查询实验 - 成功")
    void getExperimentByKey_Success() {
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);

        Experiment found = experimentService.getExperimentByKey("exp_test_001");

        assertNotNull(found);
        assertEquals("exp_test_001", found.getExpId());
        verify(experimentMapper).selectByExpId("exp_test_001");
    }

    @Test
    @DisplayName("查询实验列表 - 无筛选")
    void listExperiments_NoFilter() {
        List<Experiment> experiments = List.of(testExperiment);
        when(experimentMapper.selectList(any())).thenReturn(experiments);

        List<Experiment> result = experimentService.listExperiments(null, null);

        assertEquals(1, result.size());
        verify(experimentMapper).selectList(any());
    }

    @Test
    @DisplayName("启动实验 - 成功")
    void startExperiment_Success() {
        testExperiment.setStatus("draft");
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);
        when(bucketMapper.selectActiveBuckets("exp_test_001")).thenReturn(List.of(testBucket, testBucket2));
        when(experimentMapper.updateById(any(Experiment.class))).thenReturn(1);

        Experiment started = experimentService.startExperiment(1L);

        assertNotNull(started);
        assertEquals("running", started.getStatus());
        verify(experimentMapper).updateById(any(Experiment.class));
        verify(bucketMapper).selectActiveBuckets("exp_test_001");
    }

    @Test
    @DisplayName("启动实验 - 实验不存在")
    void startExperiment_NotFound() {
        when(experimentMapper.selectById(999L)).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> experimentService.startExperiment(999L));

        assertTrue(exception.getMessage().contains("实验不存在"));
        verify(experimentMapper, never()).updateById(any(Experiment.class));
    }

    @Test
    @DisplayName("暂停实验 - 成功")
    void pauseExperiment_Success() {
        testExperiment.setStatus("running");
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);
        when(experimentMapper.updateById(any(Experiment.class))).thenReturn(1);

        Experiment paused = experimentService.stopExperiment(1L);

        assertNotNull(paused);
        assertEquals("paused", paused.getStatus());
        verify(experimentMapper).updateById(any(Experiment.class));
    }

    @Test
    @DisplayName("删除实验 - 成功")
    void deleteExperiment_Success() {
        testExperiment.setStatus("draft");
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);
        when(bucketMapper.deleteByExpId("exp_test_001")).thenReturn(1);
        when(experimentMapper.deleteById(1L)).thenReturn(1);

        experimentService.deleteExperiment(1L);

        verify(bucketMapper).deleteByExpId("exp_test_001");
        verify(experimentMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除实验 - 运行中不能删除")
    void deleteExperiment_RunningCannotDelete() {
        testExperiment.setStatus("running");
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);

        VictorException exception = assertThrows(VictorException.class,
                () -> experimentService.deleteExperiment(1L));

        assertTrue(exception.getMessage().contains("运行中的实验不能删除"));
        verify(experimentMapper, never()).deleteById(1L);
    }

    @Test
    @DisplayName("查询实验版本 - 成功")
    void getExperimentBuckets_Success() {
        List<Bucket> buckets = List.of(testBucket);
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);
        when(bucketMapper.selectActiveBuckets("exp_test_001")).thenReturn(buckets);

        List<Bucket> result = experimentService.getExperimentBuckets(1L);

        assertEquals(1, result.size());
        assertEquals("control", result.get(0).getBucketId());
        verify(bucketMapper).selectActiveBuckets("exp_test_001");
    }

    @Test
    @DisplayName("更新实验 - 成功")
    void updateExperiment_Success() {
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);
        when(experimentMapper.updateById(any(Experiment.class))).thenReturn(1);

        Experiment update = new Experiment();
        update.setId(1L);
        update.setName("更新后的名称");

        Experiment result = experimentService.updateExperiment(update);

        assertNotNull(result);
        verify(experimentMapper).updateById(any(Experiment.class));
    }

    @Test
    @DisplayName("更新实验 - 不存在")
    void updateExperiment_NotFound() {
        when(experimentMapper.selectById(999L)).thenReturn(null);

        Experiment update = new Experiment();
        update.setId(999L);
        update.setName("更新后的名称");

        VictorException exception = assertThrows(VictorException.class,
                () -> experimentService.updateExperiment(update));

        assertTrue(exception.getMessage().contains("实验不存在"));
        verify(experimentMapper, never()).updateById(any(Experiment.class));
    }
}
