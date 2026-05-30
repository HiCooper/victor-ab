package com.gateflow.victor.service.bucket;

import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.BucketMapper;
import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.enums.ExperimentStatus;import com.gateflow.victor.common.exception.VictorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 版本管理服务
 */
@Service
@RequiredArgsConstructor
public class BucketService {

    private final BucketMapper bucketMapper;
    private final ExperimentMapper experimentMapper;

    /**
     * 创建版本
     *
     * @param bucket 版本信息
     * @return 创建的版本
     */
    @Transactional(rollbackFor = Exception.class)
    public Bucket createBucket(Bucket bucket) {
        // 验证实验是否存在（通过业务expId查询）
        Experiment experiment = experimentMapper.selectByExpId(bucket.getExpId());
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(bucket.getExpId()));
        }

        // 只有草稿状态可以添加版本
        if (!ExperimentStatus.DRAFT.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.VARIANT_ONLY_DRAFT_ADD);
        }

        // 验证桶范围
        validateBucketRange(bucket, experiment);

        bucket.setCreatedAt(LocalDateTime.now());
        bucketMapper.insert(bucket);

        return bucket;
    }

    /**
     * 批量创建版本
     *
     * @param buckets 版本列表
     * @return 创建的版本列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Bucket> createBuckets(List<Bucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            throw new VictorException(ErrorCode.VARIANT_EMPTY_LIST);
        }

        // 所有版本应属于同一实验（通过业务expId查询）
        String expId = buckets.get(0).getExpId();
        Experiment experiment = experimentMapper.selectByExpId(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        // 只有草稿状态可以添加版本
        if (!ExperimentStatus.DRAFT.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.VARIANT_ONLY_DRAFT_ADD);
        }

        LocalDateTime now = LocalDateTime.now();
        for (Bucket bucket : buckets) {
            bucket.setExpId(expId);
            validateBucketRange(bucket, experiment);
            bucket.setCreatedAt(now);
            bucketMapper.insert(bucket);
        }

        return buckets;
    }

    /**
     * 更新版本
     *
     * @param bucket 版本信息
     * @return 更新后的版本
     */
    @Transactional(rollbackFor = Exception.class)
    public Bucket updateBucket(Bucket bucket) {
        Bucket existing = bucketMapper.selectById(bucket.getId());
        if (existing == null) {
            throw new VictorException(ErrorCode.VARIANT_NOT_FOUND, String.valueOf(bucket.getId()));
        }

        // 验证实验状态（通过业务expId查询）
        Experiment experiment = experimentMapper.selectByExpId(existing.getExpId());
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND);
        }

        // 只有草稿状态可以修改版本
        if (!ExperimentStatus.DRAFT.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.VARIANT_ONLY_DRAFT_MODIFY);
        }

        // 验证桶范围
        validateBucketRange(bucket, experiment);

        bucketMapper.updateById(bucket);

        return bucket;
    }

    /**
     * 删除版本
     *
     * @param bucketId 版本ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBucket(Long bucketId) {
        Bucket bucket = bucketMapper.selectById(bucketId);
        if (bucket == null) {
            throw new VictorException(ErrorCode.VARIANT_NOT_FOUND, String.valueOf(bucketId));
        }

        // 验证实验状态（通过业务expId查询）
        Experiment experiment = experimentMapper.selectByExpId(bucket.getExpId());
        if (experiment != null && !ExperimentStatus.DRAFT.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.VARIANT_ONLY_DRAFT_DELETE);
        }

        bucketMapper.deleteById(bucketId);
    }

    /**
     * 查询实验的所有版本（通过业务expId查询）
     *
     * @param expId 业务实验ID
     * @return 版本列表
     */
    public List<Bucket> getBucketsByExperiment(String expId) {
        return bucketMapper.selectByExpId(expId);
    }

    /**
     * 查询实验的所有版本（通过主键expId查询）
     *
     * @param expId 数据库主键ID
     * @return 版本列表
     */
    public List<Bucket> getBucketsByExperimentId(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            return Collections.emptyList();
        }
        return bucketMapper.selectByExpId(experiment.getExpId());
    }

    /**
     * 查询版本详情
     *
     * @param bucketId 版本ID
     * @return 版本信息
     */
    public Bucket getBucket(Long bucketId) {
        return bucketMapper.selectById(bucketId);
    }

    /**
     * 验证桶范围
     */
    private void validateBucketRange(Bucket bucket, Experiment experiment) {
        if (bucket.getBucketStart() < 0 ||
            bucket.getBucketEnd() > 9999) {
            throw new VictorException(ErrorCode.VARIANT_BUCKET_OUT_OF_RANGE);
        }
        if (bucket.getBucketStart() > bucket.getBucketEnd()) {
            throw new VictorException(ErrorCode.VARIANT_BUCKET_INVALID);
        }
    }
}