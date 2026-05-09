package com.gateflow.victor.service.experiment;

import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.common.util.ExperimentIdGenerator;
import com.gateflow.victor.domain.dto.ExperimentCreateRequest;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.VariantMapper;
import com.gateflow.victor.common.exception.VictorException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final VariantMapper variantMapper;
    private final ExperimentLifecycleService lifecycleService;
    private final VariantVersionService versionService;

    /**
     * 创建实验
     *
     * @param experiment 实验信息
     * @param variants 版本列表
     * @return 创建的实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment createExperiment(Experiment experiment, List<Variant> variants) {
        // 验证层是否存在
        Layer layer = layerMapper.selectById(experiment.getLayerId());
        if (layer == null) {
            throw new VictorException(ErrorCode.LAYER_NOT_FOUND, String.valueOf(experiment.getLayerId()));
        }

        // 验证桶范围
        validateBucketRange(experiment, layer);

        // 计算变体桶边界（如果前端未提供）
        List<Variant> processedVariants = calculateVariantBucketBoundaries(variants, experiment);

        // 验证版本桶范围总和
        validateVariantBucketRanges(processedVariants, experiment);

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
        if (processedVariants != null && !processedVariants.isEmpty()) {
            String version = versionService.generateVersion();
            LocalDateTime now = LocalDateTime.now();
            for (Variant variant : processedVariants) {
                variant.setExpId(experiment.getId());
                variant.setVersion(version);
                variant.setIsActive(true);
                variant.setCreatedAt(now);
                variantMapper.insert(variant);
            }
            log.info("Created experiment {} with version {}", experiment.getId(), version);
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
            experiment.setBucketStart(existing.getBucketStart());
            experiment.setBucketEnd(existing.getBucketEnd());
        }

        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        return experiment;
    }

    /**
     * 更新实验并创建新版本
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment updateExperimentWithVariants(Experiment experiment, List<ExperimentCreateRequest.VariantRequest> variantRequests) {
        log.info("Updating experiment {} with new version", experiment.getId());
        
        // 1. 更新实验基本信息
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);
        
        // 2. 根据trafficPercentage自动计算bucket边界
        List<ExperimentCreateRequest.VariantRequest> processedVariants = calculateBucketBoundaries(variantRequests);
        
        // 3. 转换VariantRequest为Variant实体
        List<Variant> newVariants = processedVariants.stream().map(req -> {
            Variant variant = new Variant();
            variant.setVariantKey(req.getVariantKey());
            variant.setName(req.getName());
            variant.setBucketStart(req.getBucketStart());
            variant.setBucketEnd(req.getBucketEnd());
            variant.setParams(req.getParams());
            return variant;
        }).collect(Collectors.toList());
        
        // 4. 创建新版本
        String newVersion = versionService.createNewVersion(experiment.getId(), newVariants);
        
        log.info("Updated experiment {} with new version {}", experiment.getId(), newVersion);
        return experimentMapper.selectById(experiment.getId());
    }
    
    /**
     * 根据trafficPercentage自动计算bucket边界
     * 后端使用0-9999的bucket系统表示0%-100%
     */
    private List<ExperimentCreateRequest.VariantRequest> calculateBucketBoundaries(
            List<ExperimentCreateRequest.VariantRequest> variantRequests) {
        
        if (variantRequests == null || variantRequests.isEmpty()) {
            return variantRequests;
        }
        
        // 验证trafficPercentage必填
        for (ExperimentCreateRequest.VariantRequest req : variantRequests) {
            if (req.getTrafficPercentage() == null) {
                throw new VictorException(ErrorCode.BKT_TRAFFIC_PERCENTAGE, req.getVariantKey() + " 缺少trafficPercentage字段");
            }
        }
        
        // 验证trafficPercentage总和
        int totalPercentage = variantRequests.stream()
                .mapToInt(ExperimentCreateRequest.VariantRequest::getTrafficPercentage)
                .sum();
        
        if (totalPercentage != 100) {
            throw new VictorException(ErrorCode.BKT_TRAFFIC_PERCENTAGE, "当前为: " + totalPercentage + "%");
        }
        
        // 自动计算bucket边界
        int currentBucket = 0;
        for (ExperimentCreateRequest.VariantRequest req : variantRequests) {
            int percentage = req.getTrafficPercentage();
            // bucket = percentage * 100 (0-100% -> 0-9999)
            req.setBucketStart(currentBucket * 100);
            req.setBucketEnd((currentBucket + percentage) * 100 - 1);
            currentBucket += percentage;
            
            log.debug("Variant {}: trafficPercentage={}%, bucketStart={}, bucketEnd={}",
                    req.getVariantKey(), percentage, req.getBucketStart(), req.getBucketEnd());
        }
        
        return variantRequests;
    }

    /**
     * 启动实验
     *
     * @param expId 实验ID
     * @return 更新后的实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment startExperiment(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        // 只有草稿状态或暂停状态可以启动/恢复
        String status = experiment.getStatus();        
        if (!ExperimentStatus.DRAFT.getCode().equals(status) &&
            !ExperimentStatus.PAUSED.getCode().equals(status)) {
            throw new VictorException(ErrorCode.EXP_ONLY_DRAFT_CAN_START);
        }

        // 验证活跃版本是否存在
        List<Variant> variants = variantMapper.selectActiveVariants(expId);
        if (variants.isEmpty()) {
            throw new VictorException(ErrorCode.EXP_NO_ACTIVE_VARIANT);
        }

        experiment.setStatus(ExperimentStatus.RUNNING.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        log.info("Started experiment {} with {} active variants", expId, variants.size());

        return experiment;
    }

    /**
     * 提交实验审核
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment submitForReview(Long expId, String operator) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.REVIEW);

        
        experiment.setStatus(ExperimentStatus.REVIEW.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(), 
            from, ExperimentStatus.REVIEW, operator, "提交审核");

        return experiment;
    }

    /**
     * 审批通过实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment approveExperiment(Long expId, String operator, String comment) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.RAMP);

        
        experiment.setStatus(ExperimentStatus.RAMP.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(), 
            from, ExperimentStatus.RAMP, operator, "审批通过: " + comment);

        return experiment;
    }

    /**
     * 驳回实验
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
     * 渐进放量（灰度升级）
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment rampUpExperiment(Long expId, Integer newBucketEnd, String operator) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        if (newBucketEnd != null) {
            if (newBucketEnd <= experiment.getBucketStart() || newBucketEnd > 9999) {
                throw new VictorException(ErrorCode.BKT_INVALID_END, String.valueOf(newBucketEnd));
            }
            experiment.setBucketEnd(newBucketEnd);
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        
        // 如果是从草稿直接到RAMP（审批通过后）
        if (from == ExperimentStatus.DRAFT) {
            lifecycleService.validateTransition(from, ExperimentStatus.RAMP);
        }

        
        experiment.setStatus(ExperimentStatus.RAMP.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(), 
            from, ExperimentStatus.RAMP, operator, "渐进放量至: " + newBucketEnd);

        return experiment;
    }

    /**
     * 恢复实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment resumeExperiment(Long expId, String operator) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.RUNNING);

        
        experiment.setStatus(ExperimentStatus.RUNNING.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(), 
            from, ExperimentStatus.RUNNING, operator, "恢复实验");

        return experiment;
    }

    /**
     * 暂停实验（别名）
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment pauseExperiment(Long expId) {
        return stopExperiment(expId);
    }

    /**
     * 停止实验
     *
     * @param expId 实验ID
     * @return 更新后的实验
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment stopExperiment(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        experiment.setStatus(ExperimentStatus.PAUSED.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        return experiment;
    }

    /**
     * 结束实验进入分析
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment analyzeExperiment(Long expId, String operator) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.ANALYZING);

        
        experiment.setStatus(ExperimentStatus.ANALYZING.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(), 
            from, ExperimentStatus.ANALYZING, operator, "结束实验进入分析");

        return experiment;
    }

    /**
     * 生成决策建议
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment makeDecision(Long expId, String decision, String operator) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        
        if (!ExperimentStatus.ANALYZING.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.EXP_DECISION_MUST_ANALYZING);
        }

        
        experiment.setStatus(ExperimentStatus.DECISION.getCode());
        experiment.setUpdatedAt(LocalDateTime.now());
        experimentMapper.updateById(experiment);

        lifecycleService.logTransition(expId, experiment.getExpId(), 
            from, ExperimentStatus.DECISION, operator, "决策: " + decision);

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
            from, ExperimentStatus.ARCHIVE, operator, "归档决策: " + decision);

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

        // 创建新实验
        Experiment cloned = new Experiment();
        cloned.setExpId(newExpId);
        cloned.setName(source.getName() + " (Clone)");
        cloned.setDescription(source.getDescription());
        cloned.setLayerId(source.getLayerId());
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
        List<Variant> sourceVariants = variantMapper.selectByExpId(expId);
        if (!sourceVariants.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (Variant sourceVariant : sourceVariants) {
                Variant clonedVariant = new Variant();
                clonedVariant.setExpId(cloned.getId());
                clonedVariant.setVariantKey(sourceVariant.getVariantKey());
                clonedVariant.setName(sourceVariant.getName());
                clonedVariant.setBucketStart(sourceVariant.getBucketStart());
                clonedVariant.setBucketEnd(sourceVariant.getBucketEnd());
                clonedVariant.setParams(sourceVariant.getParams());
                clonedVariant.setCreatedAt(now);
                variantMapper.insert(clonedVariant);
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

        // 只有草稿或已停止状态可以删除
        if (ExperimentStatus.RUNNING.getCode().equals(experiment.getStatus()) ||
            ExperimentStatus.RAMP.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.EXP_CANNOT_DELETE_RUNNING);
        }

        // 先删除版本
        variantMapper.deleteByExpId(expId);

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
     * @param status 状态（可选）
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
     * 分页查询实验列表
     *
     * @param current 当前页码
     * @param size 每页数量
     * @param layerId 层ID（可选）
     * @param status 状态（可选）
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
     * @param expId 实验ID
     * @return 版本列表
     */
    public List<Variant> getExperimentVariants(Long expId) {
        return variantMapper.selectActiveVariants(expId);
    }

    /**
     * 查询实验的所有历史版本
     *
     * @param expId 实验ID
     * @return 所有版本列表
     */
    public List<Variant> getAllExperimentVariants(Long expId) {
        return variantMapper.selectByExpId(expId);
    }

    /**
     * 查询实验的指定版本
     *
     * @param expId 实验ID
     * @param version 版本号
     * @return 版本列表
     */
    public List<Variant> getExperimentVariantsByVersion(Long expId, String version) {
        return versionService.getVariantsByVersion(expId, version);
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
     * 计算变体桶边界（当变体未提供 bucketStart/bucketEnd 时）
     */
    private List<Variant> calculateVariantBucketBoundaries(List<Variant> variants, Experiment experiment) {
        if (variants == null || variants.isEmpty()) {
            return variants;
        }

        boolean needsCalculation = variants.stream()
            .anyMatch(v -> v.getBucketStart() == null || v.getBucketEnd() == null);

        if (!needsCalculation) {
            return variants;
        }

        int totalPercentage = variants.stream()
            .mapToInt(v -> getVariantTrafficPercentage(v, experiment))
            .sum();

        if (totalPercentage == 0) {
            int bucketRange = experiment.getBucketEnd() - experiment.getBucketStart() + 1;
            int perVariant = bucketRange / variants.size();
            int remainder = bucketRange % variants.size();
            int currentStart = experiment.getBucketStart();

            for (int i = 0; i < variants.size(); i++) {
                Variant v = variants.get(i);
                int end = currentStart + perVariant + (i < remainder ? 1 : 0) - 1;
                v.setBucketStart(currentStart);
                v.setBucketEnd(end);
                currentStart = end + 1;
            }
        } else {
            if (totalPercentage != 100) {
                throw new VictorException("BKT_002", "流量比例总和必须为100%，当前为: " + totalPercentage + "%");
            }

            int currentStart = experiment.getBucketStart();
            for (Variant v : variants) {
                int percentage = getVariantTrafficPercentage(v, experiment);
                int bucketSpan = (int) Math.round(percentage / 100.0 * (experiment.getBucketEnd() - experiment.getBucketStart() + 1));
                v.setBucketStart(currentStart);
                v.setBucketEnd(currentStart + bucketSpan - 1);
                currentStart += bucketSpan;
            }
        }

        return variants;
    }

    private int getVariantTrafficPercentage(Variant variant, Experiment experiment) {
        // If params contain traffic info, use it; otherwise equal distribution
        if (variant.getParams() != null) {
            try {
                com.fasterxml.jackson.databind.JsonNode params =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(variant.getParams());
                if (params.has("trafficPercentage")) {
                    return params.get("trafficPercentage").asInt();
                }
            } catch (Exception ignored) { }
        }
        return 0;
    }

    /**
     * 验证桶范围
     */
    private void validateBucketRange(Experiment experiment, Layer layer) {
        if (experiment.getBucketStart() < 0 || experiment.getBucketEnd() >= 10000) {
            throw new VictorException("BKT_001", "Bucket range must be in [0, 9999]");
        }
        if (experiment.getBucketStart() > experiment.getBucketEnd()) {
            throw new VictorException("BKT_001", "Bucket start must be less than bucket end");
        }

        // 验证层内桶范围不冲突
        checkLayerBucketConflict(experiment, layer);
    }

    /**
     * 检测层内其他运行中实验的桶范围冲突
     */
    private void checkLayerBucketConflict(Experiment experiment, Layer layer) {
        List<Experiment> layerExperiments = experimentMapper.selectByLayerId(layer.getId());
        if (layerExperiments == null) {
            return;
        }

        int newStart = experiment.getBucketStart();
        int newEnd = experiment.getBucketEnd();

        for (Experiment existing : layerExperiments) {
            if (existing.getId() != null && existing.getId().equals(experiment.getId())) {
                continue;
            }
            String status = existing.getStatus();
            if (ExperimentStatus.RUNNING.getCode().equals(status) ||
                ExperimentStatus.RAMP.getCode().equals(status) ||
                ExperimentStatus.PAUSED.getCode().equals(status)) {

                Integer existingStart = existing.getBucketStart();
                Integer existingEnd = existing.getBucketEnd();

                if (existingStart != null && existingEnd != null &&
                    newStart <= existingEnd && newEnd >= existingStart) {
                    throw new VictorException("BKT_003",
                        "实验 '" + existing.getName() + "' 的桶范围 [" + existingStart + ", " + existingEnd +
                        "] 与当前范围 [" + newStart + ", " + newEnd + "] 重叠");
                }
            }
        }
    }

    /**
     * 验证版本桶范围
     */
    private void validateVariantBucketRanges(List<Variant> variants, Experiment experiment) {
        if (variants == null || variants.isEmpty()) {
            return;
        }

        int totalBuckets = 0;
        for (Variant variant : variants) {
            if (variant.getBucketStart() < experiment.getBucketStart() ||
                variant.getBucketEnd() > experiment.getBucketEnd()) {
                throw new VictorException("BKT_002", "Variant bucket range must be within experiment bucket range");
            }
            totalBuckets += (variant.getBucketEnd() - variant.getBucketStart() + 1);
        }

        int expectedBuckets = experiment.getBucketEnd() - experiment.getBucketStart() + 1;
        if (totalBuckets != expectedBuckets) {
            throw new VictorException("BKT_002", "Variant bucket ranges must cover entire experiment bucket range");
        }
    }

}