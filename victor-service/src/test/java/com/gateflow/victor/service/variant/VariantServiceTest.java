package com.gateflow.victor.service.variant;

import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
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
 * BucketService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BucketServiceTest {

    @Mock
    private BucketMapper bucketMapper;

    @Mock
    private ExperimentMapper experimentMapper;

    @InjectMocks
    private BucketService bucketService;

    private Experiment testExperiment;
    private Bucket testVariant;

    @BeforeEach
    void setUp() {
        testExperiment = new Experiment();
        testExperiment.setId(1L);
        testExperiment.setExpId("exp_test_001");
        testExperiment.setName("测试实验");
        testExperiment.setStatus("draft");

        testVariant = new Bucket();
        testVariant.setId(1L);
        testVariant.setExpId("exp_test_001");
        testVariant.setBucketId("control");
        testVariant.setName("对照组");
        testVariant.setBucketStart(0);
        testVariant.setBucketEnd(499);
    }

    @Test
    @DisplayName("创建版本 - 成功")
    void createVariant_Success() {
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);
        when(bucketMapper.insert(any(Variant.class))).thenReturn(1);

        Bucket created = bucketService.createVariant(testVariant);

        assertNotNull(created);
        assertEquals("control", created.getBucketId());
        verify(bucketMapper).insert(any(Variant.class));
    }

    @Test
    @DisplayName("创建版本 - 实验不存在")
    void createVariant_ExperimentNotFound() {
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.createVariant(testVariant));

        assertTrue(exception.getMessage().contains("实验不存在"));
        verify(bucketMapper, never()).insert(any(Variant.class));
    }

    @Test
    @DisplayName("创建版本 - 实验非草稿状态")
    void createVariant_NotDraftExperiment() {
        testExperiment.setStatus("running");
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.createVariant(testVariant));

        assertTrue(exception.getMessage().contains("草稿"));
        verify(bucketMapper, never()).insert(any(Variant.class));
    }

    @Test
    @DisplayName("批量创建版本 - 成功")
    void createVariants_Success() {
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);
        when(bucketMapper.insert(any(Variant.class))).thenReturn(1);

        Bucket variant2 = new Bucket();
        variant2.setExpId("exp_test_001");
        variant2.setBucketId("treatment");
        variant2.setBucketStart(500);
        variant2.setBucketEnd(999);

        List<Bucket> variants = List.of(testVariant, variant2);
        List<Bucket> created = bucketService.createVariants(variants);

        assertEquals(2, created.size());
        verify(bucketMapper, times(2)).insert(any(Variant.class));
    }

    @Test
    @DisplayName("批量创建版本 - 空列表")
    void createVariants_EmptyList() {
        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.createVariants(Collections.emptyList()));

        assertTrue(exception.getMessage().contains("不能为空"));
        verify(bucketMapper, never()).insert(any(Variant.class));
    }

    @Test
    @DisplayName("查询版本 - 成功")
    void getVariant_Success() {
        when(bucketMapper.selectById(1L)).thenReturn(testVariant);

        Bucket found = bucketService.getVariant(1L);

        assertNotNull(found);
        assertEquals("control", found.getBucketId());
        verify(bucketMapper).selectById(1L);
    }

    @Test
    @DisplayName("查询版本 - 不存在")
    void getVariant_NotFound() {
        when(bucketMapper.selectById(999L)).thenReturn(null);

        Bucket found = bucketService.getVariant(999L);

        assertNull(found);
        verify(bucketMapper).selectById(999L);
    }

    @Test
    @DisplayName("查询实验版本列表 - 成功")
    void getVariantsByExperiment_Success() {
        List<Bucket> variants = List.of(testVariant);
        when(bucketMapper.selectByExpId("exp_test_001")).thenReturn(variants);

        List<Bucket> result = bucketService.getVariantsByExperiment("exp_test_001");

        assertEquals(1, result.size());
        verify(bucketMapper).selectByExpId("exp_test_001");
    }

    @Test
    @DisplayName("更新版本 - 成功")
    void updateVariant_Success() {
        Bucket existing = new Bucket();
        existing.setId(1L);
        existing.setExpId("exp_test_001");
        existing.setBucketStart(0);
        existing.setBucketEnd(499);

        when(bucketMapper.selectById(1L)).thenReturn(existing);
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);
        when(bucketMapper.updateById(any(Variant.class))).thenReturn(1);

        Bucket update = new Bucket();
        update.setId(1L);
        update.setBucketStart(0);
        update.setBucketEnd(499);
        update.setName("更新后的名称");

        Bucket result = bucketService.updateVariant(update);

        assertNotNull(result);
        verify(bucketMapper).updateById(any(Variant.class));
    }

    @Test
    @DisplayName("更新版本 - 版本不存在")
    void updateVariant_NotFound() {
        when(bucketMapper.selectById(999L)).thenReturn(null);

        Bucket update = new Bucket();
        update.setId(999L);
        update.setName("更新后的名称");

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.updateVariant(update));

        assertTrue(exception.getMessage().contains("变体不存在"));
        verify(bucketMapper, never()).updateById(any(Variant.class));
    }

    @Test
    @DisplayName("更新版本 - 实验非草稿状态")
    void updateVariant_NotDraftExperiment() {
        Bucket existing = new Bucket();
        existing.setId(1L);
        existing.setExpId("exp_test_001");

        testExperiment.setStatus("running");
        when(bucketMapper.selectById(1L)).thenReturn(existing);
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);

        Bucket update = new Bucket();
        update.setId(1L);
        update.setName("更新后的名称");

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.updateVariant(update));

        assertTrue(exception.getMessage().contains("草稿"));
        verify(bucketMapper, never()).updateById(any(Variant.class));
    }

    @Test
    @DisplayName("删除版本 - 成功")
    void deleteVariant_Success() {
        Bucket existing = new Bucket();
        existing.setId(1L);
        existing.setExpId("exp_test_001");

        when(bucketMapper.selectById(1L)).thenReturn(existing);
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);
        when(bucketMapper.deleteById(1L)).thenReturn(1);

        bucketService.deleteVariant(1L);

        verify(bucketMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除版本 - 版本不存在")
    void deleteVariant_NotFound() {
        when(bucketMapper.selectById(999L)).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.deleteVariant(999L));

        assertTrue(exception.getMessage().contains("变体不存在"));
        verify(bucketMapper, never()).deleteById(999L);
    }

    @Test
    @DisplayName("桶范围验证 - 超出实验范围")
    void validateBucketRange_OutOfExperimentRange() {
        Bucket variant = new Bucket();
        variant.setExpId("exp_test_001");
        variant.setBucketId("control");
        variant.setBucketStart(0);
        variant.setBucketEnd(9999); // 超出 0-9999

        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);

        // BucketService.validateBucketRange checks 0-9999 bounds
        // 9999 is within range, but let's test 10000 which is out of range
        variant.setBucketEnd(10000);

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.createVariant(variant));

        assertTrue(exception.getMessage().contains("超出"));
        verify(bucketMapper, never()).insert(any(Variant.class));
    }
}
