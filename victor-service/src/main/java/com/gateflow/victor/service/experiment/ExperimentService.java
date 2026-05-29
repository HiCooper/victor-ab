package com.gateflow.victor.service.experiment;

import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.common.util.BucketIdGenerator;
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

import java.util.Collections;

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

        // 计算变体桶边界（如果前端未提供）
        List<Variant> processedVariants = calculateVariantBucketBoundaries(variants);

        // 验证版本桶范围
        validateVariantBucketRanges(processedVariants);

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
                variant.setExpId(experiment.getExpId());
                variant.setVersion(version);
                variant.setIsActive(true);
                variant.setCreatedAt(now);
                variantMapper.insert(variant);
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
            variant.setBucketId(req.getVariantKey());
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
     */
    @Transactional(rollbackFor = Exception.class)
    public Experiment startExperiment(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }

        ExperimentStatus from = ExperimentStatus.fromCode(experiment.getStatus());
        lifecycleService.validateTransition(from, ExperimentStatus.RUNNING);

        List<Variant> variants = variantMapper.selectActiveVariants(experiment.getExpId());
        if (variants.isEmpty()) {
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
        List<Variant> sourceVariants = variantMapper.selectByExpId(source.getExpId());
        if (!sourceVariants.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (Variant sourceVariant : sourceVariants) {
                Variant clonedVariant = new Variant();
                clonedVariant.setExpId(cloned.getExpId());
                clonedVariant.setBucketId(BucketIdGenerator.generate());
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

        // 只有非运行中状态可以删除
        if (ExperimentStatus.RUNNING.getCode().equals(experiment.getStatus())) {
            throw new VictorException(ErrorCode.EXP_CANNOT_DELETE_RUNNING);
        }

        // 先删除版本（通过业务expId）
        variantMapper.deleteByExpId(experiment.getExpId());

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
     * @param expId 实验主键ID
     * @return 版本列表
     */
    public List<Variant> getExperimentVariants(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            return Collections.emptyList();
        }
        return variantMapper.selectActiveVariants(experiment.getExpId());
    }

    /**
     * 查询实验的所有历史版本
     *
     * @param expId 实验主键ID
     * @return 所有版本列表
     */
    public List<Variant> getAllExperimentVariants(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            return Collections.emptyList();
        }
        return variantMapper.selectByExpId(experiment.getExpId());
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
     * 默认使用完整的 0-9999 桶范围
     */
    private List<Variant> calculateVariantBucketBoundaries(List<Variant> variants) {
        if (variants == null || variants.isEmpty()) {
            return variants;
        }

        boolean needsCalculation = variants.stream()
            .anyMatch(v -> v.getBucketStart() == null || v.getBucketEnd() == null);

        if (!needsCalculation) {
            return variants;
        }

        int totalPercentage = variants.stream()
            .mapToInt(v -> getVariantTrafficPercentage(v))
            .sum();

        if (totalPercentage == 0) {
            // 未指定比例，均分 0-9999
            int bucketRange = 10000;
            int perVariant = bucketRange / variants.size();
            int remainder = bucketRange % variants.size();
            int currentStart = 0;

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

            int currentStart = 0;
            for (Variant v : variants) {
                int percentage = getVariantTrafficPercentage(v);
                int bucketSpan = (int) Math.round(percentage / 100.0 * 10000);
                v.setBucketStart(currentStart);
                v.setBucketEnd(currentStart + bucketSpan - 1);
                currentStart += bucketSpan;
            }
        }

        return variants;
    }

    private int getVariantTrafficPercentage(Variant variant) {
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
     * 验证版本桶范围
     */
    private void validateVariantBucketRanges(List<Variant> variants) {
        if (variants == null || variants.isEmpty()) {
            return;
        }

        int totalBuckets = 0;
        for (Variant variant : variants) {
            if (variant.getBucketStart() == null || variant.getBucketEnd() == null) {
                throw new VictorException("BKT_002", "Variant bucket range must not be null");
            }
            if (variant.getBucketStart() < 0 || variant.getBucketEnd() > 9999) {
                throw new VictorException("BKT_002", "Variant bucket range must be within [0, 9999]");
            }
            totalBuckets += (variant.getBucketEnd() - variant.getBucketStart() + 1);
        }

        // 变体桶范围总和必须覆盖 0-9999 (即 10000 个桶)
        if (totalBuckets != 10000) {
            throw new VictorException("BKT_002", "Variant bucket ranges must cover entire 0-9999 bucket range, total: " + totalBuckets);
        }
    }

}