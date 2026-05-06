package com.gateflow.victor.service.bucketing;

import com.gateflow.victor.bucketing.BucketEngine;
import com.gateflow.victor.bucketing.BucketResult;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 分桶服务 - 核心分流计算
 */
@Service
@RequiredArgsConstructor
public class BucketingService {

    private final ExperimentMapper experimentMapper;
    private final LayerMapper layerMapper;
    private final VariantMapper variantMapper;

    /**
     * 获取用户在指定实验中的分桶结果
     *
     * @param userId       用户ID
     * @param experimentKey 实验标识
     * @return 分桶结果
     */
    public BucketResult getVariant(String userId, String experimentKey) {
        // 查询实验
        Experiment experiment = experimentMapper.selectByExpId(experimentKey);
        if (experiment == null || !isBucketable(experiment.getStatus())) {
            return BucketResult.notHit(userId, experimentKey, -1);
        }

        // 查询层
        Layer layer = layerMapper.selectById(experiment.getLayerId());
        if (layer == null) {
            return BucketResult.notHit(userId, experimentKey, -1);
        }

        // 查询版本
        List<Variant> variants = variantMapper.selectByExpId(experiment.getId());

        // 构建实验规格
        BucketEngine.ExperimentSpec spec = buildExperimentSpec(experiment, layer, variants);

        // 计算分桶
        return BucketEngine.computeBucketResult(userId, spec);
    }

    /**
     * 获取用户所有实验的分桶结果
     *
     * @param userId 用户ID
     * @return 分桶结果列表
     */
    public List<BucketResult> getAllVariants(String userId) {
        // 查询所有运行中的实验
        List<Experiment> experiments = experimentMapper.selectRunningExperiments();

        if (experiments.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询关联数据
        Set<Long> layerIds = experiments.stream()
                .map(Experiment::getLayerId)
                .collect(Collectors.toSet());

        Map<Long, Layer> layerMap = new HashMap<>();
        for (Long layerId : layerIds) {
            Layer layer = layerMapper.selectById(layerId);
            if (layer != null) {
                layerMap.put(layerId, layer);
            }
        }

        Set<Long> expIds = experiments.stream()
                .map(Experiment::getId)
                .collect(Collectors.toSet());

        Map<Long, List<Variant>> variantMap = new HashMap<>();
        for (Long expId : expIds) {
            variantMap.put(expId, variantMapper.selectByExpId(expId));
        }

        // 构建实验规格列表
        List<BucketEngine.ExperimentSpec> specs = experiments.stream()
                .filter(exp -> isBucketable(exp.getStatus()))
                .map(exp -> buildExperimentSpec(
                        exp,
                        layerMap.get(exp.getLayerId()),
                        variantMap.getOrDefault(exp.getId(), Collections.emptyList())
                ))
                .toList();

        // 批量计算分桶
        return BucketEngine.computeAllBucketResults(userId, specs);
    }

    /**
     * 构建实验规格
     */
    private BucketEngine.ExperimentSpec buildExperimentSpec(
            Experiment experiment, Layer layer, List<Variant> variants) {

        List<BucketEngine.VariantSpec> variantSpecs = variants.stream()
                .map(v -> new BucketEngine.VariantSpec(
                        v.getVariantKey(),
                        v.getBucketStart(),
                        v.getBucketEnd()
                ))
                .toList();

        return new BucketEngine.ExperimentSpec(
                experiment.getExpId(),
                layer.getLayerId(),
                layer.getSalt(),
                experiment.getBucketStart(),
                experiment.getBucketEnd(),
                variantSpecs
        );
    }

    /**
     * 判断实验是否可分桶
     */
    private boolean isBucketable(String status) {
        return "running".equals(status) || "ramp".equals(status);
    }
}