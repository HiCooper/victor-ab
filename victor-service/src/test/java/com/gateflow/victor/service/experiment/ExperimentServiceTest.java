package com.gateflow.victor.service.experiment;

import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
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
import static org.mockito.ArgumentMatchers.anyLong;
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
    private VariantMapper variantMapper;

    @InjectMocks
    private ExperimentService experimentService;

    private Experiment testExperiment;
    private Layer testLayer;
    private Variant testVariant;
    private Variant testVariant2;

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
        testExperiment.setBucketStart(0);
        testExperiment.setBucketEnd(999);
        testExperiment.setStatus("draft");

        // 两个variant覆盖完整的实验桶范围 0-999 (共1000个桶)
        testVariant = new Variant();
        testVariant.setId(1L);
        testVariant.setExpId(1L);
        testVariant.setVariantKey("control");
        testVariant.setBucketStart(0);
        testVariant.setBucketEnd(499);

        testVariant2 = new Variant();
        testVariant2.setId(2L);
        testVariant2.setExpId(1L);
        testVariant2.setVariantKey("treatment");
        testVariant2.setBucketStart(500);
        testVariant2.setBucketEnd(999);
    }

    @Test
    @DisplayName("创建实验 - 成功")
    void createExperiment_Success() {
        when(layerMapper.selectById(1L)).thenReturn(testLayer);
        when(experimentMapper.insert(any(Experiment.class))).thenReturn(1);
        when(variantMapper.insert(any(Variant.class))).thenReturn(1);

        // 使用两个variant覆盖完整桶范围
        List<Variant> variants = List.of(testVariant, testVariant2);
        Experiment created = experimentService.createExperiment(testExperiment, variants);

        assertNotNull(created);
        assertEquals("draft", created.getStatus());
        verify(experimentMapper).insert(any(Experiment.class));
        verify(variantMapper, times(2)).insert(any(Variant.class));
    }

    @Test
    @DisplayName("创建实验 - 层不存在")
    void createExperiment_LayerNotFound() {
        when(layerMapper.selectById(1L)).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> experimentService.createExperiment(testExperiment, Collections.emptyList()));

        assertTrue(exception.getMessage().contains("Layer not found"));
        verify(experimentMapper, never()).insert(any(Experiment.class));
    }

    @Test
    @DisplayName("创建实验 - 无版本")
    void createExperiment_NoVariants() {
        when(layerMapper.selectById(1L)).thenReturn(testLayer);
        when(experimentMapper.insert(any(Experiment.class))).thenReturn(1);

        Experiment created = experimentService.createExperiment(testExperiment, null);

        assertNotNull(created);
        assertEquals("draft", created.getStatus());
        verify(variantMapper, never()).insert(any(Variant.class));
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
        when(variantMapper.selectByExpId(1L)).thenReturn(List.of(testVariant, testVariant2));
        when(experimentMapper.updateById(any(Experiment.class))).thenReturn(1);

        Experiment started = experimentService.startExperiment(1L);

        assertNotNull(started);
        assertEquals("running", started.getStatus());
        verify(experimentMapper).updateById(any(Experiment.class));
        verify(variantMapper).selectByExpId(1L);
    }

    @Test
    @DisplayName("启动实验 - 实验不存在")
    void startExperiment_NotFound() {
        when(experimentMapper.selectById(999L)).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> experimentService.startExperiment(999L));

        assertTrue(exception.getMessage().contains("Experiment not found"));
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
        when(variantMapper.deleteByExpId(1L)).thenReturn(1);
        when(experimentMapper.deleteById(anyLong())).thenReturn(1);

        experimentService.deleteExperiment(1L);

        verify(variantMapper).deleteByExpId(1L);
        verify(experimentMapper).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除实验 - 运行中不能删除")
    void deleteExperiment_RunningCannotDelete() {
        testExperiment.setStatus("running");
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);

        VictorException exception = assertThrows(VictorException.class,
                () -> experimentService.deleteExperiment(1L));

        assertTrue(exception.getMessage().contains("Running experiment cannot be deleted"));
        verify(experimentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("查询实验版本 - 成功")
    void getExperimentVariants_Success() {
        List<Variant> variants = List.of(testVariant);
        when(variantMapper.selectByExpId(1L)).thenReturn(variants);

        List<Variant> result = experimentService.getExperimentVariants(1L);

        assertEquals(1, result.size());
        assertEquals("control", result.get(0).getVariantKey());
        verify(variantMapper).selectByExpId(1L);
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

        assertTrue(exception.getMessage().contains("Experiment not found"));
        verify(experimentMapper, never()).updateById(any(Experiment.class));
    }
}