package com.gateflow.victor.service.experiment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

        // 计算分桶桶边界（如果前端未提供）
        List<Bucket> processedBuckets = calculateBucketRanges(buckets);

        // 验证版本桶范围
        validateBucketRanges(processedBuckets);

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

        // 1. 更新实验基本信息
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        // 2. 根据trafficPercentage自动计算bucket边界
        List<ExperimentCreateRequest.BucketRequest> processedBuckets = calculateBucketBoundaries(bucketRequests);

        // 3. 转换BucketRequest为Bucket实体
        List<Bucket> newBuckets = processedBuckets.stream().map(req -> {
            Bucket bucket = new Bucket();
            bucket.setBucketId(BucketIdGenerator.generate());
            bucket.setName(req.getName());
            bucket.setBucketStart(req.getBucketStart());
            bucket.setBucketEnd(req.getBucketEnd());
            bucket.setParams(req.getParams());
            return bucket;
        }).collect(Collectors.toList());

        // 4. 创建新版本
        String newVersion = versionService.createNewVersion(experiment.getId(), newBuckets);

        log.info("Updated experiment {} with new version {}", experiment.getId(), newVersion);
        return experimentMapper.selectById(experiment.getId());
    }

    /**
     * 根据trafficPercentage自动计算bucket边界
     * 后端使用0-9999的bucket系统表示0%-100%
     */
    private List<ExperimentCreateRequest.BucketRequest> calculateBucketBoundaries(
            List<ExperimentCreateRequest.BucketRequest> bucketRequests) {

        if (bucketRequests == null || bucketRequests.isEmpty()) {
            return bucketRequests;
        }

        // 验证trafficPercentage必填
        for (ExperimentCreateRequest.BucketRequest req : bucketRequests) {
            if (req.getTrafficPercentage() == null) {
                throw new VictorException(ErrorCode.BKT_TRAFFIC_PERCENTAGE, req.getBucketKey() + " 缺少trafficPercentage字段");
            }
        }

        // 验证trafficPercentage总和
        int totalPercentage = bucketRequests.stream()
                .mapToInt(ExperimentCreateRequest.BucketRequest::getTrafficPercentage)
                .sum();

        if (totalPercentage != 100) {
            throw new VictorException(ErrorCode.BKT_TRAFFIC_PERCENTAGE, "当前为: " + totalPercentage + "%");
        }

        // 自动计算bucket边界
        int currentBucket = 0;
        for (ExperimentCreateRequest.BucketRequest req : bucketRequests) {
            int percentage = req.getTrafficPercentage();
            // bucket = percentage * 100 (0-100% -> 0-9999)
            req.setBucketStart(currentBucket * 100);
            req.setBucketEnd((currentBucket + percentage) * 100 - 1);
            currentBucket += percentage;

            log.debug("Bucket {}: trafficPercentage={}%, bucketStart={}, bucketEnd={}",
                    req.getBucketKey(), percentage, req.getBucketStart(), req.getBucketEnd());
        }

        return bucketRequests;
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

        experiment.setStatus(ExperimentStatus.RUNNING.getCode());
        experiment.setStartTime(LocalDateTime.now());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(),
                from, ExperimentStatus.RUNNING, "system", "启动实验");

        return experiment;
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

        experiment.setStatus(ExperimentStatus.PENDING_APPROVAL.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(),
                from, ExperimentStatus.PENDING_APPROVAL, operator, "提交审批");

        return experiment;
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

        experiment.setStatus(ExperimentStatus.RUNNING.getCode());
        experiment.setStartTime(LocalDateTime.now());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(),
                from, ExperimentStatus.RUNNING, operator, "审批通过: " + comment);

        return experiment;
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

        experiment.setStatus(ExperimentStatus.DRAFT.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(),
                from, ExperimentStatus.DRAFT, operator, "驳回: " + reason);

        return experiment;
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

        experiment.setStatus(ExperimentStatus.STOPPED.getCode());
        experiment.setEndTime(LocalDateTime.now());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(),
                from, ExperimentStatus.STOPPED, "system", "停止实验");

        return experiment;
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

        experiment.setStatus(ExperimentStatus.ARCHIVE.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(),
                from, ExperimentStatus.ARCHIVE, operator, "归档: " + decision);

        return experiment;
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

    /**
     * 计算分桶桶边界（当分桶未提供 bucketStart/bucketEnd 时）
     * 默认使用完整的 0-9999 桶范围
     */
    private List<Bucket> calculateBucketRanges(List<Bucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return buckets;
        }

        boolean needsCalculation = buckets.stream()
                .anyMatch(v -> v.getBucketStart() == null || v.getBucketEnd() == null);

        if (!needsCalculation) {
            return buckets;
        }

        int totalPercentage = buckets.stream()
                .mapToInt(v -> getBucketTrafficPercentage(v))
                .sum();

        if (totalPercentage == 0) {
            // 未指定比例，均分 0-9999
            int bucketRange = 10000;
            int perBucket = bucketRange / buckets.size();
            int remainder = bucketRange % buckets.size();
            int currentStart = 0;

            for (int i = 0; i < buckets.size(); i++) {
                Bucket v = buckets.get(i);
                int end = currentStart + perBucket + (i < remainder ? 1 : 0) - 1;
                v.setBucketStart(currentStart);
                v.setBucketEnd(end);
                currentStart = end + 1;
            }
        } else {
            if (totalPercentage != 100) {
                throw new VictorException("BKT_002", "流量比例总和必须为100%，当前为: " + totalPercentage + "%");
            }

            int currentStart = 0;
            for (Bucket v : buckets) {
                int percentage = getBucketTrafficPercentage(v);
                int bucketSpan = (int) Math.round(percentage / 100.0 * 10000);
                v.setBucketStart(currentStart);
                v.setBucketEnd(currentStart + bucketSpan - 1);
                currentStart += bucketSpan;
            }
        }

        return buckets;
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

    /**
     * 验证版本桶范围
     */
    private void validateBucketRanges(List<Bucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return;
        }

        int totalBuckets = 0;
        for (Bucket bucket : buckets) {
            if (bucket.getBucketStart() == null || bucket.getBucketEnd() == null) {
                throw new VictorException("BKT_002", "Bucket bucket range must not be null");
            }
            if (bucket.getBucketStart() < 0 || bucket.getBucketEnd() > 9999) {
                throw new VictorException("BKT_002", "Bucket bucket range must be within [0, 9999]");
            }
            totalBuckets += (bucket.getBucketEnd() - bucket.getBucketStart() + 1);
        }

        // 分桶桶范围总和必须覆盖 0-9999 (即 10000 个桶)
        if (totalBuckets != 10000) {
            throw new VictorException("BKT_002", "Bucket bucket ranges must cover entire 0-9999 bucket range, total: " + totalBuckets);
        }
    }

}