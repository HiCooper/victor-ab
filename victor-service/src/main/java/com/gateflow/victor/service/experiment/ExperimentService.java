package com.gateflow.victor.service.experiment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gateflow.victor.common.bucketing.LayerTrafficAllocator;
import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.common.util.BucketIdGenerator;
import com.gateflow.victor.common.util.ExperimentIdGenerator;
import com.gateflow.victor.domain.dto.ExperimentCreateRequest;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.infra.mapper.BucketMapper;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实验管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentService {

    private final ExperimentMapper experimentMapper;
    private final LayerMapper layerMapper;
    private final BucketMapper bucketMapper;
    private final ExperimentLifecycleService lifecycleService;
    private final BucketVersionService versionService;
    private final ObjectMapper objectMapper;

    /**
     * 创建实验
     *
     * @param experiment 实验信息
     * @param buckets    版本列表
     * @return 创建的实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment createExperiment(Experiment experiment, List<Bucket> buckets) {
        // 验证层是否存在
        Layer layer = layerMapper.selectById(experiment.getLayerId());
        if (layer == null) {
            throw new VictorException(ErrorCode.LAYER_NOT_FOUND, String.valueOf(experiment.getLayerId()));
        }

        // 解析实验在层内占用的桶段（层内互斥）：显式区间 / 按百分比自动分配 / 默认整层
        LayerTrafficAllocator.Range expRange = resolveExperimentRange(
                experiment.getLayerId(), null,
                experiment.getBucketStart(), experiment.getBucketEnd(),
                experiment.getLayerTrafficPercentage());
        experiment.setBucketStart(expRange.start());
        experiment.setBucketEnd(expRange.end());

        // 在实验桶段内切分变体并校验完全覆盖
        List<Bucket> processedBuckets = distributeVariantRanges(buckets, expRange);
        validateVariantRanges(processedBuckets, expRange);

        // 生成实验ID（格式：年最后一位+月日+随机数，共7位）
        String expId = ExperimentIdGenerator.generate();
        experiment.setExpId(expId);

        // 设置初始状态
        experiment.setStatus(ExperimentStatus.DRAFT.getCode());
        experiment.setCreatedAt(LocalDateTime.now());
        experiment.setUpdatedAt(LocalDateTime.now());

        // 插入实验
        experimentMapper.insert(experiment);

        log.info("Created experiment with ID: {}", expId);

        // 插入版本（使用版本控制服务）
        if (processedBuckets != null && !processedBuckets.isEmpty()) {
            String version = versionService.generateVersion();
            LocalDateTime now = LocalDateTime.now();
            for (Bucket bucket : processedBuckets) {
                bucket.setExpId(experiment.getExpId());
                bucket.setVersion(version);
                bucket.setIsActive(true);
                bucket.setCreatedAt(now);
                bucketMapper.insert(bucket);
            }
            log.info("Created experiment {} with version {}", experiment.getExpId(), version);
        }

        return experiment;
    }

    /**
     * 更新实验
     *
     * @param experiment 实验信息
     * @return 更新后的实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment updateExperiment(Experiment experiment) {
        Experiment existing = experimentMapper.selectById(experiment.getId());
        if (existing == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(experiment.getId()));
        }

        // 只有草稿状态可以修改核心配置
        if (!ExperimentStatus.DRAFT.getCode().equals(existing.getStatus())) {
            // 非草稿状态只能修改描述等非核心字段
            experiment.setLayerId(existing.getLayerId());
        }

        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        return experiment;
    }

    /**
     * 更新实验并创建新版本
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment updateExperimentWithBuckets(Experiment experiment, List<ExperimentCreateRequest.BucketRequest> bucketRequests) {
        log.info("Updating experiment {} with new version", experiment.getId());

        Experiment existing = experimentMapper.selectById(experiment.getId());
        if (existing == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(experiment.getId()));
        }

        // 实验在层内的桶段在编辑变体时保持不变（改流量占位是另一动作），变体在该桶段内切分
        LayerTrafficAllocator.Range expRange = experimentRangeOf(existing);

        // 1. 更新实验基本信息（不允许通过本接口改动层内桶段）
        experiment.setBucketStart(existing.getBucketStart());
        experiment.setBucketEnd(existing.getBucketEnd());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        // 2. 在实验桶段内按 trafficPercentage 切分变体
        List<Integer> percentages = bucketRequests.stream()
                .map(req -> {
                    if (req.getTrafficPercentage() == null) {
                        throw new VictorException(ErrorCode.BKT_TRAFFIC_PERCENTAGE,
                                req.getBucketKey() + " 缺少trafficPercentage字段");
                    }
                    return req.getTrafficPercentage();
                })
                .toList();
        List<LayerTrafficAllocator.Range> ranges = splitVariants(expRange, percentages);

        // 3. 转换BucketRequest为Bucket实体
        List<Bucket> newBuckets = new java.util.ArrayList<>(bucketRequests.size());
        for (int i = 0; i < bucketRequests.size(); i++) {
            ExperimentCreateRequest.BucketRequest req = bucketRequests.get(i);
            Bucket bucket = new Bucket();
            bucket.setBucketId(BucketIdGenerator.generate());
            bucket.setName(req.getName());
            bucket.setBucketStart(ranges.get(i).start());
            bucket.setBucketEnd(ranges.get(i).end());
            bucket.setParams(req.getParams());
            newBuckets.add(bucket);
        }

        // 4. 创建新版本
        String newVersion = versionService.createNewVersion(experiment.getId(), newBuckets);

        log.info("Updated experiment {} with new version {}", experiment.getId(), newVersion);
        return experimentMapper.selectById(experiment.getId());
    }

    // ===================== 层内互斥 / 桶段分配 =====================

    /**
     * 取实验在层内占用的桶段（存量实验未设置时默认整层 0-9999）。
     */
    private LayerTrafficAllocator.Range experimentRangeOf(Experiment experiment) {
        int start = experiment.getBucketStart() != null ? experiment.getBucketStart() : LayerTrafficAllocator.LAYER_MIN;
        int end = experiment.getBucketEnd() != null ? experiment.getBucketEnd() : LayerTrafficAllocator.LAYER_MAX;
        return new LayerTrafficAllocator.Range(start, end);
    }

    /**
     * 查询同层中已占用桶段的实验（仅运行中实验真正占用流量），可排除指定实验。
     */
    private List<LayerTrafficAllocator.Range> occupiedRangesInLayer(Long layerId, Long excludeExpId) {
        List<Experiment> inLayer = experimentMapper.selectByLayerId(layerId);
        if (inLayer == null) {
            return Collections.emptyList();
        }
        return inLayer.stream()
                .filter(e -> ExperimentStatus.RUNNING.getCode().equals(e.getStatus()))
                .filter(e -> excludeExpId == null || !excludeExpId.equals(e.getId()))
                .filter(e -> e.getBucketStart() != null && e.getBucketEnd() != null)
                .map(this::experimentRangeOf)
                .toList();
    }

    /**
     * 解析实验在层内的桶段：
     * <ul>
     *   <li>显式给定 [reqStart, reqEnd] → 校验越界与同层重叠后采用（手动占位）；</li>
     *   <li>给定 layerTrafficPct → 在层内自动寻找该宽度的首个空闲段；</li>
     *   <li>都未给定 → 默认整层 [0, 9999]，仅当层内无运行中实验占用时允许。</li>
     * </ul>
     */
    private LayerTrafficAllocator.Range resolveExperimentRange(
            Long layerId, Long excludeExpId, Integer reqStart, Integer reqEnd, Integer layerTrafficPct) {

        List<LayerTrafficAllocator.Range> occupied = occupiedRangesInLayer(layerId, excludeExpId);

        if (reqStart != null && reqEnd != null) {
            LayerTrafficAllocator.Range candidate;
            try {
                candidate = new LayerTrafficAllocator.Range(reqStart, reqEnd);
                LayerTrafficAllocator.validateNoOverlap(candidate, occupied);
            } catch (IllegalArgumentException e) {
                throw new VictorException(ErrorCode.BKT_OUT_OF_RANGE, e.getMessage());
            } catch (IllegalStateException e) {
                throw new VictorException(ErrorCode.BKT_OVERLAP, e.getMessage());
            }
            return candidate;
        }

        if (layerTrafficPct != null) {
            if (layerTrafficPct <= 0 || layerTrafficPct > 100) {
                throw new VictorException(ErrorCode.BKT_TRAFFIC_PERCENTAGE,
                        "layerTrafficPercentage 必须在 (0,100] 之间，当前为: " + layerTrafficPct);
            }
            try {
                // 百分比 -> 桶宽：1% = 100 桶
                return LayerTrafficAllocator.findFreeGap(layerTrafficPct * 100, occupied);
            } catch (IllegalStateException e) {
                throw new VictorException(ErrorCode.BKT_OVERLAP,
                        "层内剩余空间不足以容纳 " + layerTrafficPct + "% 流量");
            }
        }

        // 默认整层；仅当层内无运行中实验占用时允许，否则要求显式指定，避免静默重叠
        if (!occupied.isEmpty()) {
            throw new VictorException(ErrorCode.BKT_OVERLAP,
                    "该层已存在运行中实验占用桶段，请显式指定实验的 bucketStart/bucketEnd 或 layerTrafficPercentage");
        }
        return new LayerTrafficAllocator.Range(LayerTrafficAllocator.LAYER_MIN, LayerTrafficAllocator.LAYER_MAX);
    }

    /**
     * 在实验桶段内为变体分配子区间。
     * 整层实验且变体已带显式区间时沿用（向后兼容旧前端）；否则按 trafficPercentage 切分。
     */
    private List<Bucket> distributeVariantRanges(List<Bucket> buckets, LayerTrafficAllocator.Range expRange) {
        if (buckets == null || buckets.isEmpty()) {
            return buckets;
        }

        boolean allExplicit = buckets.stream()
                .allMatch(b -> b.getBucketStart() != null && b.getBucketEnd() != null);
        boolean isFullLayer = expRange.start() == LayerTrafficAllocator.LAYER_MIN
                && expRange.end() == LayerTrafficAllocator.LAYER_MAX;
        if (allExplicit && isFullLayer) {
            return buckets;
        }

        List<Integer> percentages = buckets.stream()
                .map(this::getBucketTrafficPercentage)
                .collect(Collectors.toList());
        List<LayerTrafficAllocator.Range> ranges = splitVariants(expRange, percentages);
        for (int i = 0; i < buckets.size(); i++) {
            buckets.get(i).setBucketStart(ranges.get(i).start());
            buckets.get(i).setBucketEnd(ranges.get(i).end());
        }
        return buckets;
    }

    /**
     * 按百分比将实验桶段切分为变体子区间，并把分配器的校验异常转为业务异常。
     */
    private List<LayerTrafficAllocator.Range> splitVariants(
            LayerTrafficAllocator.Range expRange, List<Integer> percentages) {
        try {
            return LayerTrafficAllocator.splitByPercentage(expRange, percentages);
        } catch (IllegalArgumentException e) {
            throw new VictorException(ErrorCode.BKT_TRAFFIC_PERCENTAGE, e.getMessage());
        }
    }

    /**
     * 校验变体子区间连续、无缝隙且完全覆盖实验桶段。
     */
    private void validateVariantRanges(List<Bucket> buckets, LayerTrafficAllocator.Range expRange) {
        if (buckets == null || buckets.isEmpty()) {
            return;
        }
        List<LayerTrafficAllocator.Range> ranges = buckets.stream()
                .map(b -> {
                    if (b.getBucketStart() == null || b.getBucketEnd() == null) {
                        throw new VictorException(ErrorCode.VARIANT_BUCKET_INVALID, "变体桶范围不能为空");
                    }
                    return new LayerTrafficAllocator.Range(b.getBucketStart(), b.getBucketEnd());
                })
                .toList();
        try {
            LayerTrafficAllocator.validateVariantsCoverExperiment(expRange, ranges);
        } catch (IllegalArgumentException e) {
            throw new VictorException(ErrorCode.VARIANT_BUCKET_MUST_COVER, e.getMessage());
        }
    }

    /**
     * 激活（启动/审批通过）前校验实验桶段不与同层其他运行中实验重叠（层内互斥的权威检查点）。
     */
    private void validateNoLayerOverlapOnActivation(Experiment experiment) {
        if (experiment.getBucketStart() == null || experiment.getBucketEnd() == null) {
            return;
        }
        List<LayerTrafficAllocator.Range> occupied =
                occupiedRangesInLayer(experiment.getLayerId(), experiment.getId());
        try {
            LayerTrafficAllocator.validateNoOverlap(experimentRangeOf(experiment), occupied);
        } catch (IllegalArgumentException e) {
            throw new VictorException(ErrorCode.BKT_OUT_OF_RANGE, e.getMessage());
        } catch (IllegalStateException e) {
            throw new VictorException(ErrorCode.BKT_OVERLAP, e.getMessage());
        }
    }

    /**
     * 启动实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment startExperiment(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.RUNNING);

        List<Bucket> buckets = bucketMapper.selectActiveBuckets(experiment.getExpId());
        if (buckets.isEmpty()) {
            throw new VictorException(ErrorCode.EXP_NO_ACTIVE_VARIANT);
        }

        // 层内互斥：启动前确认桶段不与同层其他运行中实验重叠
        validateNoLayerOverlapOnActivation(experiment);

        if (!lifecycleService.tryLockExperiment(experiment.getExpId())) {
            throw new VictorException(ErrorCode.LFC_LOCK_FAILED, experiment.getExpId());
        }
        try {
            experiment.setStatus(ExperimentStatus.RUNNING.getCode());
            experiment.setStartTime(LocalDateTime.now());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentMapper.updateById(experiment);

            lifecycleService.logTransition(expId, experiment.getExpId(),
                    from, ExperimentStatus.RUNNING, "system", "启动实验");

            return experiment;
        } finally {
            lifecycleService.unlockExperiment(experiment.getExpId());
        }
    }

    /**
     * 提交审批
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment submitForApproval(Long expId, String operator) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.PENDING_APPROVAL);

        if (!lifecycleService.tryLockExperiment(experiment.getExpId())) {
            throw new VictorException(ErrorCode.LFC_LOCK_FAILED, experiment.getExpId());
        }
        try {
            experiment.setStatus(ExperimentStatus.PENDING_APPROVAL.getCode());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentMapper.updateById(experiment);

            lifecycleService.logTransition(expId, experiment.getExpId(),
                    from, ExperimentStatus.PENDING_APPROVAL, operator, "提交审批");

            return experiment;
        } finally {
            lifecycleService.unlockExperiment(experiment.getExpId());
        }
    }

    /**
     * 审批通过 — 进入运行中
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment approveExperiment(Long expId, String operator, String comment) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.RUNNING);

        // 层内互斥：进入运行中前确认桶段不与同层其他运行中实验重叠
        validateNoLayerOverlapOnActivation(experiment);

        if (!lifecycleService.tryLockExperiment(experiment.getExpId())) {
            throw new VictorException(ErrorCode.LFC_LOCK_FAILED, experiment.getExpId());
        }
        try {
            experiment.setStatus(ExperimentStatus.RUNNING.getCode());
            experiment.setStartTime(LocalDateTime.now());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentMapper.updateById(experiment);

            lifecycleService.logTransition(expId, experiment.getExpId(),
                    from, ExperimentStatus.RUNNING, operator, "审批通过: " + comment);

            return experiment;
        } finally {
            lifecycleService.unlockExperiment(experiment.getExpId());
        }
    }

    /**
     * 驳回 — 回到草稿
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment rejectExperiment(Long expId, String operator, String reason) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.DRAFT);

        if (!lifecycleService.tryLockExperiment(experiment.getExpId())) {
            throw new VictorException(ErrorCode.LFC_LOCK_FAILED, experiment.getExpId());
        }
        try {
            experiment.setStatus(ExperimentStatus.DRAFT.getCode());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentMapper.updateById(experiment);

            lifecycleService.logTransition(expId, experiment.getExpId(),
                    from, ExperimentStatus.DRAFT, operator, "驳回: " + reason);

            return experiment;
        } finally {
            lifecycleService.unlockExperiment(experiment.getExpId());
        }
    }

    /**
     * 停止实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment stopExperiment(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.STOPPED);

        if (!lifecycleService.tryLockExperiment(experiment.getExpId())) {
            throw new VictorException(ErrorCode.LFC_LOCK_FAILED, experiment.getExpId());
        }
        try {
            experiment.setStatus(ExperimentStatus.STOPPED.getCode());
            experiment.setEndTime(LocalDateTime.now());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentMapper.updateById(experiment);

            lifecycleService.logTransition(expId, experiment.getExpId(),
                    from, ExperimentStatus.STOPPED, "system", "停止实验");

            return experiment;
        } finally {
            lifecycleService.unlockExperiment(experiment.getExpId());
        }
    }

    /**
     * 归档实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment archiveExperiment(Long expId, String decision, String operator) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.ARCHIVE);

        if (!lifecycleService.tryLockExperiment(experiment.getExpId())) {
            throw new VictorException(ErrorCode.LFC_LOCK_FAILED, experiment.getExpId());
        }
        try {
            experiment.setStatus(ExperimentStatus.ARCHIVE.getCode());
            experiment.setUpdatedAt(LocalDateTime.now());
            experimentMapper.updateById(experiment);

            lifecycleService.logTransition(expId, experiment.getExpId(),
                    from, ExperimentStatus.ARCHIVE, operator, "归档: " + decision);

            return experiment;
        } finally {
            lifecycleService.unlockExperiment(experiment.getExpId());
        }
    }

    /**
     * 克隆实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment cloneExperiment(Long expId, String newExpId, String operator) {
        Experiment source = experimentMapper.selectById(expId);
        if (source == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        // 验证新 expId 唯一性
        Experiment existing = experimentMapper.selectByExpId(newExpId);
        if (existing != null) {
            throw new VictorException(ErrorCode.EXP_ALREADY_EXISTS, "Experiment ID already exists: " + newExpId);
        }

        // 创建新实验
        Experiment cloned = new Experiment();
        cloned.setExpId(newExpId);
        cloned.setName(source.getName() + " (Clone)");
        cloned.setDescription(source.getDescription());
        cloned.setLayerId(source.getLayerId());
        // 沿用源实验的层内桶段（草稿态可共存；启动时若与运行中实验冲突会被互斥校验拦截）
        cloned.setBucketStart(source.getBucketStart());
        cloned.setBucketEnd(source.getBucketEnd());
        cloned.setTargetingRules(source.getTargetingRules());
        cloned.setPrimaryMetric(source.getPrimaryMetric());
        cloned.setSecondaryMetrics(source.getSecondaryMetrics());
        cloned.setGuardrailMetrics(source.getGuardrailMetrics());
        cloned.setStatus(ExperimentStatus.DRAFT.getCode());
        cloned.setCreatedBy(operator);
        cloned.setCreatedAt(LocalDateTime.now());
        cloned.setUpdatedAt(LocalDateTime.now());

        experimentMapper.insert(cloned);

        // 克隆版本
        List<Bucket> sourceBuckets = bucketMapper.selectByExpId(source.getExpId());
        if (!sourceBuckets.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (Bucket sourceBucket : sourceBuckets) {
                Bucket clonedBucket = new Bucket();
                clonedBucket.setExpId(cloned.getExpId());
                clonedBucket.setBucketId(BucketIdGenerator.generate());
                clonedBucket.setName(sourceBucket.getName());
                clonedBucket.setBucketStart(sourceBucket.getBucketStart());
                clonedBucket.setBucketEnd(sourceBucket.getBucketEnd());
                clonedBucket.setParams(sourceBucket.getParams());
                clonedBucket.setCreatedAt(now);
                bucketMapper.insert(clonedBucket);
            }
        }

        return cloned;
    }

    /**
     * 删除实验
     *
     * @param expId 实验ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteExperiment(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        // 只有非运行中状态可以删除
        if (ExperimentStatus.RUNNING.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.EXP_CANNOT_DELETE_RUNNING);
        }

        // 先删除版本（通过业务expId）
        bucketMapper.deleteByExpId(experiment.getExpId());

        // 删除实验
        experimentMapper.deleteById(expId);
    }

    /**
     * 查询实验详情
     *
     * @param expId 实验ID
     * @return 实验信息
     */
    public Experiment getExperiment(Long expId) {
        return experimentMapper.selectById(expId);
    }

    /**
     * 根据业务ID查询实验
     *
     * @param expKey 实验业务标识
     * @return 实验信息
     */
    public Experiment getExperimentByKey(String expKey) {
        return experimentMapper.selectByExpId(expKey);
    }

    /**
     * 查询实验列表
     *
     * @param layerId 层ID（可选）
     * @param status  状态（可选）
     * @return 实验列表
     */
    public List<Experiment> listExperiments(Long layerId, String status) {
        if (layerId != null) {
            return experimentMapper.selectByLayerId(layerId);
        }
        if (status != null) {
            return experimentMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Experiment>()
                            .eq(Experiment::getStatus, status)
            );
        }
        return experimentMapper.selectList(null);
    }

    /**
     * 查询待审批的实验列表
     *
     * @return 待审批的实验列表
     */
    public List<Experiment> listPendingExperiments() {
        return experimentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Experiment>()
                        .eq(Experiment::getStatus, ExperimentStatus.PENDING_APPROVAL.getCode())
                        .orderByAsc(Experiment::getCreatedAt)
        );
    }

    /**
     * 分页查询实验列表
     *
     * @param current 当前页码
     * @param size    每页数量
     * @param layerId 层ID（可选）
     * @param status  状态（可选）
     * @return 分页结果
     */
    public Page<Experiment> listExperimentsPaged(int current, int size, Long layerId, String status) {
        Page<Experiment> page = new Page<>(current, size);
        LambdaQueryWrapper<Experiment> wrapper = new LambdaQueryWrapper<>();

        if (layerId != null) {
            wrapper.eq(Experiment::getLayerId, layerId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Experiment::getStatus, status);
        }

        wrapper.orderByDesc(Experiment::getCreatedAt);

        return experimentMapper.selectPage(page, wrapper);
    }

    /**
     * 查询实验的版本列表（默认返回当前活跃版本）
     *
     * @param expId 实验主键ID
     * @return 版本列表
     */
    public List<Bucket> getExperimentBuckets(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            return Collections.emptyList();
        }
        return bucketMapper.selectActiveBuckets(experiment.getExpId());
    }

    /**
     * 查询实验的所有历史版本
     *
     * @param expId 实验主键ID
     * @return 所有版本列表
     */
    public List<Bucket> getAllExperimentBuckets(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            return Collections.emptyList();
        }
        return bucketMapper.selectByExpId(experiment.getExpId());
    }

    /**
     * 查询实验的指定版本
     *
     * @param expId   实验ID
     * @param version 版本号
     * @return 版本列表
     */
    public List<Bucket> getExperimentBucketsByVersion(Long expId, String version) {
        return versionService.getBucketsByVersion(expId, version);
    }

    /**
     * 获取实验的版本历史
     *
     * @param expId 实验ID
     * @return 版本号列表
     */
    public List<String> getVersionHistory(Long expId) {
        return versionService.getVersionHistory(expId);
    }

    private int getBucketTrafficPercentage(Bucket bucket) {
        // If params contain traffic info, use it; otherwise equal distribution
        if (bucket.getParams() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode params =
                        objectMapper.readTree(bucket.getParams());
                if (params.has("trafficPercentage")) {
                    return params.get("trafficPercentage").asInt();
                }
            } catch (Exception e) {
                log.warn("Failed to parse bucket params JSON for bucket {}: {}", bucket.getBucketId(), e.getMessage());
            }
        }
        return 0;
    }

}