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
     * @param variant 版本信息
     * @return 创建的版本
     */
    @Transactional(rollbackFor = Exception.class)
    public Bucket createBucket(Bucket variant) {
        // 验证实验是否存在（通过业务expId查询）
        Experiment experiment = experimentMapper.selectByExpId(variant.getExpId());
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(variant.getExpId()));
        }

        // 只有草稿状态可以添加版本
        if (!ExperimentStatus.DRAFT.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.VARIANT_ONLY_DRAFT_ADD);
        }

        // 验证桶范围
        validateBucketRange(variant, experiment);

        variant.setCreatedAt(LocalDateTime.now());
        bucketMapper.insert(variant);

        return variant;
    }

    /**
     * 批量创建版本
     *
     * @param variants 版本列表
     * @return 创建的版本列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Bucket> createBuckets(List<Bucket> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new VictorException(ErrorCode.VARIANT_EMPTY_LIST);
        }

        // 所有版本应属于同一实验（通过业务expId查询）
        String expId = variants.get(0).getExpId();
        Experiment experiment = experimentMapper.selectByExpId(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        // 只有草稿状态可以添加版本
        if (!ExperimentStatus.DRAFT.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.VARIANT_ONLY_DRAFT_ADD);
        }

        LocalDateTime now = LocalDateTime.now();
        for (Bucket variant : variants) {
            variant.setExpId(expId);
            validateBucketRange(variant, experiment);
            variant.setCreatedAt(now);
            bucketMapper.insert(variant);
        }

        return variants;
    }

    /**
     * 更新版本
     *
     * @param variant 版本信息
     * @return 更新后的版本
     */
    @Transactional(rollbackFor = Exception.class)
    public Bucket updateBucket(Bucket variant) {
        Bucket existing = bucketMapper.selectById(variant.getId());
        if (existing == null) {
            throw new VictorException(ErrorCode.VARIANT_NOT_FOUND, String.valueOf(variant.getId()));
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
        validateBucketRange(variant, experiment);

        bucketMapper.updateById(variant);

        return variant;
    }

    /**
     * 删除版本
     *
     * @param variantId 版本ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBucket(Long variantId) {
        Bucket variant = bucketMapper.selectById(variantId);
        if (variant == null) {
            throw new VictorException(ErrorCode.VARIANT_NOT_FOUND, String.valueOf(variantId));
        }

        // 验证实验状态（通过业务expId查询）
        Experiment experiment = experimentMapper.selectByExpId(variant.getExpId());
        if (experiment != null && !ExperimentStatus.DRAFT.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.VARIANT_ONLY_DRAFT_DELETE);
        }

        bucketMapper.deleteById(variantId);
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
     * @param variantId 版本ID
     * @return 版本信息
     */
    public Bucket getBucket(Long variantId) {
        return bucketMapper.selectById(variantId);
    }

    /**
     * 验证桶范围
     */
    private void validateBucketRange(Bucket variant, Experiment experiment) {
        if (variant.getBucketStart() < 0 ||
            variant.getBucketEnd() > 9999) {
            throw new VictorException(ErrorCode.VARIANT_BUCKET_OUT_OF_RANGE);
        }
        if (variant.getBucketStart() > variant.getBucketEnd()) {
            throw new VictorException(ErrorCode.VARIANT_BUCKET_INVALID);
        }
    }
}