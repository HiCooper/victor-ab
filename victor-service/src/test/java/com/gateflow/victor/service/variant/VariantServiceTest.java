package com.gateflow.victor.service.variant;

import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
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
 * VariantService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class VariantServiceTest {

    @Mock
    private VariantMapper variantMapper;

    @Mock
    private ExperimentMapper experimentMapper;

    @InjectMocks
    private VariantService variantService;

    private Experiment testExperiment;
    private Variant testVariant;

    @BeforeEach
    void setUp() {
        testExperiment = new Experiment();
        testExperiment.setId(1L);
        testExperiment.setExpId("exp_test_001");
        testExperiment.setName("测试实验");
        testExperiment.setBucketStart(0);
        testExperiment.setBucketEnd(999);
        testExperiment.setStatus("draft");

        testVariant = new Variant();
        testVariant.setId(1L);
        testVariant.setExpId(1L);
        testVariant.setVariantKey("control");
        testVariant.setName("对照组");
        testVariant.setBucketStart(0);
        testVariant.setBucketEnd(499);
    }

    @Test
    @DisplayName("创建版本 - 成功")
    void createVariant_Success() {
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);
        when(variantMapper.insert(any(Variant.class))).thenReturn(1);

        Variant created = variantService.createVariant(testVariant);

        assertNotNull(created);
        assertEquals("control", created.getVariantKey());
        verify(variantMapper).insert(any(Variant.class));
    }

    @Test
    @DisplayName("创建版本 - 实验不存在")
    void createVariant_ExperimentNotFound() {
        when(experimentMapper.selectById(1L)).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> variantService.createVariant(testVariant));

        assertTrue(exception.getMessage().contains("Experiment not found"));
        verify(variantMapper, never()).insert(any(Variant.class));
    }

    @Test
    @DisplayName("创建版本 - 实验非草稿状态")
    void createVariant_NotDraftExperiment() {
        testExperiment.setStatus("running");
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);

        VictorException exception = assertThrows(VictorException.class,
                () -> variantService.createVariant(testVariant));

        assertTrue(exception.getMessage().contains("draft experiment"));
        verify(variantMapper, never()).insert(any(Variant.class));
    }

    @Test
    @DisplayName("批量创建版本 - 成功")
    void createVariants_Success() {
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);
        when(variantMapper.insert(any(Variant.class))).thenReturn(1);

        Variant variant2 = new Variant();
        variant2.setExpId(1L);
        variant2.setVariantKey("treatment");
        variant2.setBucketStart(500);
        variant2.setBucketEnd(999);

        List<Variant> variants = List.of(testVariant, variant2);
        List<Variant> created = variantService.createVariants(variants);

        assertEquals(2, created.size());
        verify(variantMapper, times(2)).insert(any(Variant.class));
    }

    @Test
    @DisplayName("批量创建版本 - 空列表")
    void createVariants_EmptyList() {
        VictorException exception = assertThrows(VictorException.class,
                () -> variantService.createVariants(Collections.emptyList()));

        assertTrue(exception.getMessage().contains("cannot be empty"));
        verify(variantMapper, never()).insert(any(Variant.class));
    }

    @Test
    @DisplayName("查询版本 - 成功")
    void getVariant_Success() {
        when(variantMapper.selectById(1L)).thenReturn(testVariant);

        Variant found = variantService.getVariant(1L);

        assertNotNull(found);
        assertEquals("control", found.getVariantKey());
        verify(variantMapper).selectById(1L);
    }

    @Test
    @DisplayName("查询版本 - 不存在")
    void getVariant_NotFound() {
        when(variantMapper.selectById(999L)).thenReturn(null);

        Variant found = variantService.getVariant(999L);

        assertNull(found);
        verify(variantMapper).selectById(999L);
    }

    @Test
    @DisplayName("查询实验版本列表 - 成功")
    void getVariantsByExperiment_Success() {
        List<Variant> variants = List.of(testVariant);
        when(variantMapper.selectByExpId(1L)).thenReturn(variants);

        List<Variant> result = variantService.getVariantsByExperiment(1L);

        assertEquals(1, result.size());
        verify(variantMapper).selectByExpId(1L);
    }

    @Test
    @DisplayName("更新版本 - 成功")
    void updateVariant_Success() {
        Variant existing = new Variant();
        existing.setId(1L);
        existing.setExpId(1L);
        existing.setBucketStart(0);
        existing.setBucketEnd(499);

        when(variantMapper.selectById(1L)).thenReturn(existing);
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);
        when(variantMapper.updateById(any(Variant.class))).thenReturn(1);

        Variant update = new Variant();
        update.setId(1L);
        update.setBucketStart(0);
        update.setBucketEnd(499);
        update.setName("更新后的名称");

        Variant result = variantService.updateVariant(update);

        assertNotNull(result);
        verify(variantMapper).updateById(any(Variant.class));
    }

    @Test
    @DisplayName("更新版本 - 版本不存在")
    void updateVariant_NotFound() {
        when(variantMapper.selectById(999L)).thenReturn(null);

        Variant update = new Variant();
        update.setId(999L);
        update.setName("更新后的名称");

        VictorException exception = assertThrows(VictorException.class,
                () -> variantService.updateVariant(update));

        assertTrue(exception.getMessage().contains("Variant not found"));
        verify(variantMapper, never()).updateById(any(Variant.class));
    }

    @Test
    @DisplayName("更新版本 - 实验非草稿状态")
    void updateVariant_NotDraftExperiment() {
        Variant existing = new Variant();
        existing.setId(1L);
        existing.setExpId(1L);

        testExperiment.setStatus("running");
        when(variantMapper.selectById(1L)).thenReturn(existing);
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);

        Variant update = new Variant();
        update.setId(1L);
        update.setName("更新后的名称");

        VictorException exception = assertThrows(VictorException.class,
                () -> variantService.updateVariant(update));

        assertTrue(exception.getMessage().contains("draft experiment"));
        verify(variantMapper, never()).updateById(any(Variant.class));
    }

    @Test
    @DisplayName("删除版本 - 成功")
    void deleteVariant_Success() {
        Variant existing = new Variant();
        existing.setId(1L);
        existing.setExpId(1L);

        when(variantMapper.selectById(1L)).thenReturn(existing);
        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);
        when(variantMapper.deleteById(anyLong())).thenReturn(1);

        variantService.deleteVariant(1L);

        verify(variantMapper).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除版本 - 版本不存在")
    void deleteVariant_NotFound() {
        when(variantMapper.selectById(999L)).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> variantService.deleteVariant(999L));

        assertTrue(exception.getMessage().contains("Variant not found"));
        verify(variantMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("桶范围验证 - 超出实验范围")
    void validateBucketRange_OutOfExperimentRange() {
        testExperiment.setBucketStart(0);
        testExperiment.setBucketEnd(499);

        Variant variant = new Variant();
        variant.setExpId(1L);
        variant.setVariantKey("control");
        variant.setBucketStart(0);
        variant.setBucketEnd(999); // 超出实验范围

        when(experimentMapper.selectById(1L)).thenReturn(testExperiment);

        VictorException exception = assertThrows(VictorException.class,
                () -> variantService.createVariant(variant));

        assertTrue(exception.getMessage().contains("bucket range"));
        verify(variantMapper, never()).insert(any(Variant.class));
    }
}