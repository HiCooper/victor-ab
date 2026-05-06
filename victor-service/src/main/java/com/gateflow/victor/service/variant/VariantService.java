package com.gateflow.victor.service.variant;

import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
import com.gateflow.victor.common.exception.VictorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 版本管理服务
 */
@Service
@RequiredArgsConstructor
public class VariantService {

    private final VariantMapper variantMapper;
    private final ExperimentMapper experimentMapper;

    /**
     * 创建版本
     *
     * @param variant 版本信息
     * @return 创建的版本
     */
    @Transactional(rollbackFor = Exception.class)
    public Variant createVariant(Variant variant) {
        // 验证实验是否存在
        Experiment experiment = experimentMapper.selectById(variant.getExpId());
        if (experiment == null) {
            throw new VictorException("Experiment not found: " + variant.getExpId());
        }

        // 只有草稿状态可以添加版本
        if (!"draft".equals(experiment.getStatus())) {
            throw new VictorException("Can only add variants to draft experiment");
        }

        // 验证桶范围
        validateBucketRange(variant, experiment);

        variant.setCreatedAt(LocalDateTime.now());
        variantMapper.insert(variant);

        return variant;
    }

    /**
     * 批量创建版本
     *
     * @param variants 版本列表
     * @return 创建的版本列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Variant> createVariants(List<Variant> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new VictorException("Variants list cannot be empty");
        }

        // 所有版本应属于同一实验
        Long expId = variants.get(0).getExpId();
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException("Experiment not found: " + expId);
        }

        // 只有草稿状态可以添加版本
        if (!"draft".equals(experiment.getStatus())) {
            throw new VictorException("Can only add variants to draft experiment");
        }

        LocalDateTime now = LocalDateTime.now();
        for (Variant variant : variants) {
            variant.setExpId(expId);
            validateBucketRange(variant, experiment);
            variant.setCreatedAt(now);
            variantMapper.insert(variant);
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
    public Variant updateVariant(Variant variant) {
        Variant existing = variantMapper.selectById(variant.getId());
        if (existing == null) {
            throw new VictorException("Variant not found: " + variant.getId());
        }

        // 验证实验状态
        Experiment experiment = experimentMapper.selectById(existing.getExpId());
        if (experiment == null) {
            throw new VictorException("Experiment not found");
        }

        // 只有草稿状态可以修改版本
        if (!"draft".equals(experiment.getStatus())) {
            throw new VictorException("Can only update variants in draft experiment");
        }

        // 验证桶范围
        validateBucketRange(variant, experiment);

        variantMapper.updateById(variant);

        return variant;
    }

    /**
     * 删除版本
     *
     * @param variantId 版本ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteVariant(Long variantId) {
        Variant variant = variantMapper.selectById(variantId);
        if (variant == null) {
            throw new VictorException("Variant not found: " + variantId);
        }

        // 验证实验状态
        Experiment experiment = experimentMapper.selectById(variant.getExpId());
        if (experiment != null && !"draft".equals(experiment.getStatus())) {
            throw new VictorException("Can only delete variants in draft experiment");
        }

        variantMapper.deleteById(variantId);
    }

    /**
     * 查询实验的所有版本
     *
     * @param expId 实验ID
     * @return 版本列表
     */
    public List<Variant> getVariantsByExperiment(Long expId) {
        return variantMapper.selectByExpId(expId);
    }

    /**
     * 查询版本详情
     *
     * @param variantId 版本ID
     * @return 版本信息
     */
    public Variant getVariant(Long variantId) {
        return variantMapper.selectById(variantId);
    }

    /**
     * 验证桶范围
     */
    private void validateBucketRange(Variant variant, Experiment experiment) {
        if (variant.getBucketStart() < experiment.getBucketStart() ||
            variant.getBucketEnd() > experiment.getBucketEnd()) {
            throw new VictorException("Variant bucket range must be within experiment bucket range");
        }
        if (variant.getBucketStart() > variant.getBucketEnd()) {
            throw new VictorException("Bucket start must be less than bucket end");
        }
    }
}