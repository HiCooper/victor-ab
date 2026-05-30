package com.gateflow.victor.service.bucketing;

import com.gateflow.victor.common.bucketing.BucketEngine;
import com.gateflow.victor.common.bucketing.BucketResult;
import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.infra.mapper.BucketMapper;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.service.whitelist.ExperimentWhitelistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分桶服务 - 核心分流计算
 */
@Service
@RequiredArgsConstructor
public class BucketingService {

    private final ExperimentMapper experimentMapper;
    private final LayerMapper layerMapper;
    private final BucketMapper bucketMapper;
    private final ExperimentWhitelistService whitelistService;

    /**
     * 获取用户在指定实验中的分桶结果
     *
     * @param userId        用户ID
     * @param experimentKey 实验标识
     * @return 分桶结果
     */
    public BucketResult getBucket(String userId, String experimentKey) {
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
        List<Bucket> buckets = bucketMapper.selectByExpId(experiment.getExpId());

        // 先检查白名单
        String whitelistBucketId = whitelistService.getBucketIdForWhitelistedUser(experiment.getExpId(), userId);
        if (whitelistBucketId != null) {
            return BucketResult.hit(userId, experimentKey, -1, whitelistBucketId, layer.getLayerId(), null);
        }

        // 构建实验规格
        BucketEngine.ExperimentSpec spec = buildExperimentSpec(experiment, layer, buckets);

        // 计算分桶
        return BucketEngine.computeBucketResult(userId, spec);
    }

    /**
     * 获取用户所有实验的分桶结果
     *
     * @param userId 用户ID
     * @return 分桶结果列表
     */
    public List<BucketResult> getAllBuckets(String userId) {
        List<Experiment> experiments = experimentMapper.selectRunningExperiments();

        if (experiments.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询关联数据 - 替代 N+1 循环
        List<Long> layerIds = experiments.stream()
                .map(Experiment::getLayerId)
                .distinct()
                .toList();
        Map<Long, Layer> layerMap = layerMapper.selectByIds(layerIds).stream()
                .collect(Collectors.toMap(Layer::getId, l -> l));

        List<String> expIds = experiments.stream()
                .map(Experiment::getExpId)
                .distinct()
                .toList();
        Map<String, List<Bucket>> bucketMap = bucketMapper.selectActiveBucketsByExpIds(expIds).stream()
                .collect(Collectors.groupingBy(Bucket::getExpId));

        List<BucketEngine.ExperimentSpec> specs = experiments.stream()
                .filter(exp -> isBucketable(exp.getStatus()))
                .map(exp -> buildExperimentSpec(
                        exp,
                        layerMap.get(exp.getLayerId()),
                        bucketMap.getOrDefault(exp.getExpId(), Collections.emptyList())
                ))
                .toList();

        return BucketEngine.computeAllBucketResults(userId, specs);
    }

    /**
     * 构建实验规格
     */
    private BucketEngine.ExperimentSpec buildExperimentSpec(
            Experiment experiment, Layer layer, List<Bucket> buckets) {

        List<BucketEngine.BucketSpec> bucketSpecs = buckets.stream()
                .map(v -> new BucketEngine.BucketSpec(
                        v.getBucketId(),
                        v.getBucketStart(),
                        v.getBucketEnd(),
                        v.getParams()
                ))
                .toList();

        // 实验级别的桶范围已从实体移除，使用 0-9999 作为默认范围
        return new BucketEngine.ExperimentSpec(
                experiment.getExpId(),
                layer.getLayerId(),
                layer.getSalt(),
                0,  // 默认从 0 开始
                9999, // 默认到 9999
                bucketSpecs
        );
    }

    /**
     * 判断实验是否可分桶
     */
    private boolean isBucketable(String status) {
        ExperimentStatus s = ExperimentStatus.fromCode(status);
        return s != null && s.isBucketable();
    }
}