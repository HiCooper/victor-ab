package com.gateflow.victor.service.bucket;

import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.infra.mapper.BucketMapper;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
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
    private Bucket testBucket;

    @BeforeEach
    void setUp() {
        testExperiment = new Experiment();
        testExperiment.setId(1L);
        testExperiment.setExpId("exp_test_001");
        testExperiment.setName("测试实验");
        testExperiment.setStatus("draft");

        testBucket = new Bucket();
        testBucket.setId(1L);
        testBucket.setExpId("exp_test_001");
        testBucket.setBucketId("control");
        testBucket.setName("对照组");
        testBucket.setBucketStart(0);
        testBucket.setBucketEnd(499);
    }

    @Test
    @DisplayName("创建版本 - 成功")
    void createBucket_Success() {
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);
        when(bucketMapper.insert(any(Bucket.class))).thenReturn(1);

        Bucket created = bucketService.createBucket(testBucket);

        assertNotNull(created);
        assertEquals("control", created.getBucketId());
        verify(bucketMapper).insert(any(Bucket.class));
    }

    @Test
    @DisplayName("创建版本 - 实验不存在")
    void createBucket_ExperimentNotFound() {
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.createBucket(testBucket));

        assertTrue(exception.getMessage().contains("实验不存在"));
        verify(bucketMapper, never()).insert(any(Bucket.class));
    }

    @Test
    @DisplayName("创建版本 - 实验非草稿状态")
    void createBucket_NotDraftExperiment() {
        testExperiment.setStatus("running");
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.createBucket(testBucket));

        assertTrue(exception.getMessage().contains("草稿"));
        verify(bucketMapper, never()).insert(any(Bucket.class));
    }

    @Test
    @DisplayName("批量创建版本 - 成功")
    void createBuckets_Success() {
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);
        when(bucketMapper.insert(any(Bucket.class))).thenReturn(1);

        Bucket bucket2 = new Bucket();
        bucket2.setExpId("exp_test_001");
        bucket2.setBucketId("treatment");
        bucket2.setBucketStart(500);
        bucket2.setBucketEnd(999);

        List<Bucket> buckets = List.of(testBucket, bucket2);
        List<Bucket> created = bucketService.createBuckets(buckets);

        assertEquals(2, created.size());
        verify(bucketMapper, times(2)).insert(any(Bucket.class));
    }

    @Test
    @DisplayName("批量创建版本 - 空列表")
    void createBuckets_EmptyList() {
        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.createBuckets(Collections.emptyList()));

        assertTrue(exception.getMessage().contains("不能为空"));
        verify(bucketMapper, never()).insert(any(Bucket.class));
    }

    @Test
    @DisplayName("查询版本 - 成功")
    void getBucket_Success() {
        when(bucketMapper.selectById(1L)).thenReturn(testBucket);

        Bucket found = bucketService.getBucket(1L);

        assertNotNull(found);
        assertEquals("control", found.getBucketId());
        verify(bucketMapper).selectById(1L);
    }

    @Test
    @DisplayName("查询版本 - 不存在")
    void getBucket_NotFound() {
        when(bucketMapper.selectById(999L)).thenReturn(null);

        Bucket found = bucketService.getBucket(999L);

        assertNull(found);
        verify(bucketMapper).selectById(999L);
    }

    @Test
    @DisplayName("查询实验版本列表 - 成功")
    void getBucketsByExperiment_Success() {
        List<Bucket> buckets = List.of(testBucket);
        when(bucketMapper.selectByExpId("exp_test_001")).thenReturn(buckets);

        List<Bucket> result = bucketService.getBucketsByExperiment("exp_test_001");

        assertEquals(1, result.size());
        verify(bucketMapper).selectByExpId("exp_test_001");
    }

    @Test
    @DisplayName("更新版本 - 成功")
    void updateBucket_Success() {
        Bucket existing = new Bucket();
        existing.setId(1L);
        existing.setExpId("exp_test_001");
        existing.setBucketStart(0);
        existing.setBucketEnd(499);

        when(bucketMapper.selectById(1L)).thenReturn(existing);
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);
        when(bucketMapper.updateById(any(Bucket.class))).thenReturn(1);

        Bucket update = new Bucket();
        update.setId(1L);
        update.setBucketStart(0);
        update.setBucketEnd(499);
        update.setName("更新后的名称");

        Bucket result = bucketService.updateBucket(update);

        assertNotNull(result);
        verify(bucketMapper).updateById(any(Bucket.class));
    }

    @Test
    @DisplayName("更新版本 - 版本不存在")
    void updateBucket_NotFound() {
        when(bucketMapper.selectById(999L)).thenReturn(null);

        Bucket update = new Bucket();
        update.setId(999L);
        update.setName("更新后的名称");

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.updateBucket(update));

        assertTrue(exception.getMessage().contains("分桶不存在"));
        verify(bucketMapper, never()).updateById(any(Bucket.class));
    }

    @Test
    @DisplayName("更新版本 - 实验非草稿状态")
    void updateBucket_NotDraftExperiment() {
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
                () -> bucketService.updateBucket(update));

        assertTrue(exception.getMessage().contains("草稿"));
        verify(bucketMapper, never()).updateById(any(Bucket.class));
    }

    @Test
    @DisplayName("删除版本 - 成功")
    void deleteBucket_Success() {
        Bucket existing = new Bucket();
        existing.setId(1L);
        existing.setExpId("exp_test_001");

        when(bucketMapper.selectById(1L)).thenReturn(existing);
        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);
        when(bucketMapper.deleteById(1L)).thenReturn(1);

        bucketService.deleteBucket(1L);

        verify(bucketMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除版本 - 版本不存在")
    void deleteBucket_NotFound() {
        when(bucketMapper.selectById(999L)).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.deleteBucket(999L));

        assertTrue(exception.getMessage().contains("分桶不存在"));
        verify(bucketMapper, never()).deleteById(999L);
    }

    @Test
    @DisplayName("桶范围验证 - 超出实验范围")
    void validateBucketRange_OutOfExperimentRange() {
        Bucket bucket = new Bucket();
        bucket.setExpId("exp_test_001");
        bucket.setBucketId("control");
        bucket.setBucketStart(0);
        bucket.setBucketEnd(9999); // 超出 0-9999

        when(experimentMapper.selectByExpId("exp_test_001")).thenReturn(testExperiment);

        // BucketService.validateBucketRange checks 0-9999 bounds
        // 9999 is within range, but let's test 10000 which is out of range
        bucket.setBucketEnd(10000);

        VictorException exception = assertThrows(VictorException.class,
                () -> bucketService.createBucket(bucket));

        assertTrue(exception.getMessage().contains("超出"));
        verify(bucketMapper, never()).insert(any(Bucket.class));
    }
}
