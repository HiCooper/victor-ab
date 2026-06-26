package com.gateflow.victor.service.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.infra.mapper.BucketMapper;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 层内互斥（mutual exclusion within a layer）专项测试。
 * <p>
 * 手动装配 ExperimentService 并注入真实 ObjectMapper，以覆盖按 trafficPercentage 切分变体的路径。
 */
class ExperimentServiceLayerExclusionTest {

    private ExperimentMapper experimentMapper;
    private LayerMapper layerMapper;
    private BucketMapper bucketMapper;
    private ExperimentLifecycleService lifecycleService;
    private BucketVersionService versionService;
    private ExperimentService service;

    private Layer layer;

    @BeforeEach
    void setUp() {
        experimentMapper = mock(ExperimentMapper.class);
        layerMapper = mock(LayerMapper.class);
        bucketMapper = mock(BucketMapper.class);
        lifecycleService = mock(ExperimentLifecycleService.class);
        versionService = mock(BucketVersionService.class);
        service = new ExperimentService(experimentMapper, layerMapper, bucketMapper,
                lifecycleService, versionService, new ObjectMapper());

        layer = new Layer();
        layer.setId(1L);
        layer.setLayerId("layer_ui");
        layer.setSalt("ui_salt");
        when(layerMapper.selectById(1L)).thenReturn(layer);
    }

    private Experiment running(long id, int start, int end) {
        Experiment e = new Experiment();
        e.setId(id);
        e.setExpId("exp_running_" + id);
        e.setLayerId(1L);
        e.setStatus("running");
        e.setBucketStart(start);
        e.setBucketEnd(end);
        return e;
    }

    private Bucket variant(String key, int pct) {
        Bucket b = new Bucket();
        b.setBucketId(key);
        b.setName(key);
        b.setParams("{\"trafficPercentage\":" + pct + "}");
        return b;
    }

    @Test
    @DisplayName("创建实验 - 显式桶段与同层运行中实验重叠 → BKT_OVERLAP")
    void create_explicitRangeOverlap_throws() {
        when(experimentMapper.selectByLayerId(1L)).thenReturn(List.of(running(1L, 0, 4999)));

        Experiment exp = new Experiment();
        exp.setLayerId(1L);
        exp.setBucketStart(2000);
        exp.setBucketEnd(6999);

        VictorException ex = assertThrows(VictorException.class,
                () -> service.createExperiment(exp, List.of(variant("c", 100))));
        assertEquals("BKT_003", ex.getErrorCode());
        verify(experimentMapper, never()).insert(any(Experiment.class));
    }

    @Test
    @DisplayName("创建实验 - 按层内流量百分比自动分配到空闲桶段")
    void create_autoAllocateByPercentage_placesInFreeGap() {
        when(experimentMapper.selectByLayerId(1L)).thenReturn(List.of(running(1L, 0, 4999)));
        when(experimentMapper.insert(any(Experiment.class))).thenReturn(1);
        when(versionService.generateVersion()).thenReturn("20260626120000");
        when(bucketMapper.insert(any(Bucket.class))).thenReturn(1);

        Experiment exp = new Experiment();
        exp.setLayerId(1L);
        exp.setLayerTrafficPercentage(30); // 宽 3000，落在 [0,4999] 之后 → [5000,7999]

        Experiment created = service.createExperiment(exp, List.of(variant("control", 50), variant("treatment", 50)));

        assertEquals(5000, created.getBucketStart());
        assertEquals(7999, created.getBucketEnd());

        // 变体在实验桶段内切分：50/50 → [5000,6499],[6500,7999]
        ArgumentCaptor<Bucket> captor = ArgumentCaptor.forClass(Bucket.class);
        verify(bucketMapper, times(2)).insert(captor.capture());
        List<Bucket> inserted = captor.getAllValues();
        assertEquals(5000, inserted.get(0).getBucketStart());
        assertEquals(6499, inserted.get(0).getBucketEnd());
        assertEquals(6500, inserted.get(1).getBucketStart());
        assertEquals(7999, inserted.get(1).getBucketEnd());
    }

    @Test
    @DisplayName("创建实验 - 层为空时默认占满整层")
    void create_defaultFullLayer_whenLayerEmpty() {
        when(experimentMapper.selectByLayerId(1L)).thenReturn(List.of());
        when(experimentMapper.insert(any(Experiment.class))).thenReturn(1);
        when(versionService.generateVersion()).thenReturn("v1");
        when(bucketMapper.insert(any(Bucket.class))).thenReturn(1);

        Experiment exp = new Experiment();
        exp.setLayerId(1L);

        Experiment created = service.createExperiment(exp, List.of(variant("control", 100)));

        assertEquals(0, created.getBucketStart());
        assertEquals(9999, created.getBucketEnd());
    }

    @Test
    @DisplayName("创建实验 - 默认整层但层内已有运行中实验 → BKT_OVERLAP")
    void create_defaultFullLayer_butLayerOccupied_throws() {
        when(experimentMapper.selectByLayerId(1L)).thenReturn(List.of(running(1L, 0, 4999)));

        Experiment exp = new Experiment();
        exp.setLayerId(1L); // 未指定桶段，默认整层 → 与已占用冲突

        VictorException ex = assertThrows(VictorException.class,
                () -> service.createExperiment(exp, List.of(variant("c", 100))));
        assertEquals("BKT_003", ex.getErrorCode());
    }

    @Test
    @DisplayName("启动实验 - 桶段与同层其他运行中实验重叠 → BKT_OVERLAP，不切换状态")
    void start_overlapWithRunning_throws() {
        Experiment draft = new Experiment();
        draft.setId(2L);
        draft.setExpId("exp_b");
        draft.setLayerId(1L);
        draft.setStatus("draft");
        draft.setBucketStart(0);
        draft.setBucketEnd(9999);

        when(experimentMapper.selectById(2L)).thenReturn(draft);
        when(bucketMapper.selectActiveBuckets("exp_b")).thenReturn(List.of(variant("c", 100)));
        when(experimentMapper.selectByLayerId(1L)).thenReturn(List.of(draft, running(1L, 5000, 9999)));

        VictorException ex = assertThrows(VictorException.class, () -> service.startExperiment(2L));
        assertEquals("BKT_003", ex.getErrorCode());
        verify(experimentMapper, never()).updateById(any(Experiment.class));
        verify(lifecycleService, never()).tryLockExperiment(anyString());
    }
}
