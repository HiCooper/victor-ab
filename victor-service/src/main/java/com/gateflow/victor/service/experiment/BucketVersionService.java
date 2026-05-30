package com.gateflow.victor.service.experiment;

import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.BucketMapper;
import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.exception.VictorException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实验版本控制服务
 *
 * 功能：
 * 1. 编辑实验时自动创建新版本
 * 2. 查询版本历史
 * 3. 版本回滚
 * 4. 版本对比
 * 5. 清理旧版本
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BucketVersionService {

    private final BucketMapper bucketMapper;
    private final ExperimentMapper experimentMapper;

    /**
     * 生成版本号（时间戳格式）
     *
     * @return 版本号，例如: 20260506143000
     */
    public String generateVersion() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    /**
     * 创建新版本（编辑实验时调用）
     *
     * 流程：
     * 1. 将当前所有版本标记为非活跃
     * 2. 生成新版本号
     * 3. 插入新版本的分桶配置
     *
     * @param expId 实验主键ID
     * @param newVariants 新的分桶配置列表
     * @return 新版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public String createNewVersion(Long expId, List<Bucket> newVariants) {
        if (newVariants == null || newVariants.isEmpty()) {
            throw new VictorException(ErrorCode.VARIANT_EMPTY_LIST);
        }

        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }
        String bizExpId = experiment.getExpId();

        // 1. 验证分桶配置（仅验证variant_key唯一性）
        validateVariantKeys(newVariants);

        // 2. 自动重新分配分桶边界（确保无重叠、无间隙）
        // 注意：仅在 Bucket 没有预设 bucketStart/bucketEnd 时才重新分配
        // 如果前端已通过 trafficPercentage 指定了比例（由 ExperimentService.calculateBucketBoundaries 计算），则跳过
        boolean hasPredefinedBoundaries = newVariants.stream()
            .anyMatch(v -> v.getBucketStart() != null && v.getBucketEnd() != null);
        
        if (!hasPredefinedBoundaries) {
            redistributeBucketRanges(newVariants);
            log.info("Auto-redistributed bucket ranges for {} variants", newVariants.size());
        } else {
            log.info("Using predefined bucket boundaries for {} variants", newVariants.size());
        }

        // 3. 将当前版本标记为非活跃
        bucketMapper.deactivateAllBuckets(bizExpId);
        log.info("Deactivated all variants for experiment {}", bizExpId);

        // 4. 生成新版本号
        String newVersion = generateVersion();
        LocalDateTime now = LocalDateTime.now();

        // 5. 插入新版本的分桶
        for (Bucket variant : newVariants) {
            variant.setId(null); // 清除ID，作为新记录插入
            variant.setExpId(bizExpId);
            variant.setVersion(newVersion);
            variant.setIsActive(true);
            variant.setCreatedAt(now);
            bucketMapper.insert(variant);
        }

        log.info("Created new version {} for experiment {} with {} variants, bucket ranges: {}",
            newVersion, bizExpId, newVariants.size(),
            newVariants.stream().map(v -> v.getBucketId() + "[" + v.getBucketStart() + "-" + v.getBucketEnd() + "]")
                .collect(Collectors.joining(", ")));

        return newVersion;
    }

    /**
     * 获取实验的当前活跃版本
     *
     * @param expId 实验主键ID
     * @return 活跃版本列表
     */
    public List<Bucket> getActiveVariants(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            return List.of();
        }
        List<Bucket> variants = bucketMapper.selectActiveBuckets(experiment.getExpId());
        if (variants.isEmpty()) {
            log.warn("No active variants found for experiment {}", expId);
        }
        return variants;
    }

    /**
     * 获取实验的所有版本列表
     *
     * @param expId 实验主键ID
     * @return 所有版本列表
     */
    public List<Bucket> getAllVariants(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            return List.of();
        }
        return bucketMapper.selectByExpId(experiment.getExpId());
    }

    /**
     * 获取实验的指定版本
     *
     * @param expId 实验主键ID
     * @param version 版本号
     * @return 版本列表
     */
    public List<Bucket> getVariantsByVersion(Long expId, String version) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }
        List<Bucket> variants = bucketMapper.selectByExpIdAndVersion(experiment.getExpId(), version);
        if (variants.isEmpty()) {
            throw new VictorException(ErrorCode.VER_NOT_FOUND, String.valueOf(version));
        }
        return variants;
    }

    /**
     * 获取实验的所有版本号列表
     *
     * @param expId 实验主键ID
     * @return 版本号列表（按时间倒序）
     */
    public List<String> getVersionHistory(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            return List.of();
        }
        return bucketMapper.selectVersionsByExpId(experiment.getExpId());
    }

    /**
     * 回滚到指定版本
     *
     * 流程：
     * 1. 验证目标版本存在
     * 2. 将当前版本标记为非活跃
     * 3. 激活目标版本
     *
     * @param expId 实验主键ID
     * @param targetVersion 目标版本号
     * @return 回滚后的版本列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Bucket> rollbackToVersion(Long expId, String targetVersion) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }
        String bizExpId = experiment.getExpId();

        // 1. 验证目标版本存在
        List<Bucket> targetVariants = bucketMapper.selectByExpIdAndVersion(bizExpId, targetVersion);
        if (targetVariants.isEmpty()) {
            throw new VictorException(ErrorCode.VER_NOT_FOUND, String.valueOf(targetVersion));
        }

        // 2. 将当前版本标记为非活跃
        bucketMapper.deactivateAllBuckets(bizExpId);
        log.info("Deactivated all variants for experiment {} before rollback", bizExpId);

        // 3. 激活目标版本
        int activated = bucketMapper.activateVersion(bizExpId, targetVersion);
        if (activated == 0) {
            throw new VictorException(ErrorCode.VER_ACTIVATE_FAILED, String.valueOf(targetVersion));
        }

        log.info("Rolled back experiment {} to version {}", bizExpId, targetVersion);

        return targetVariants;
    }

    /**
     * 对比两个版本的差异
     *
     * @param expId 实验主键ID
     * @param version1 版本1
     * @param version2 版本2
     * @return 版本对比结果
     */
    public VersionComparison compareVersions(Long expId, String version1, String version2) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }
        String bizExpId = experiment.getExpId();

        List<Bucket> variants1 = bucketMapper.selectByExpIdAndVersion(bizExpId, version1);
        List<Bucket> variants2 = bucketMapper.selectByExpIdAndVersion(bizExpId, version2);

        if (variants1.isEmpty()) {
            throw new VictorException("Version not found: " + version1);
        }
        if (variants2.isEmpty()) {
            throw new VictorException("Version not found: " + version2);
        }

        VersionComparison comparison = new VersionComparison();
        comparison.setVersion1(version1);
        comparison.setVersion2(version2);
        comparison.setVariants1(variants1);
        comparison.setVariants2(variants2);

        // 计算差异
        comparison.setHasDifferences(!variants1.equals(variants2));
        comparison.setVariantCount1(variants1.size());
        comparison.setVariantCount2(variants2.size());

        return comparison;
    }

    /**
     * 清理旧版本（保留最近N个版本）
     *
     * @param expId 实验主键ID
     * @param keepCount 保留的版本数量
     * @return 删除的版本数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupOldVersions(Long expId, int keepCount) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }
        String bizExpId = experiment.getExpId();

        List<String> versions = bucketMapper.selectVersionsByExpId(bizExpId);

        if (versions.size() <= keepCount) {
            log.info("No old versions to cleanup for experiment {} (current: {}, keep: {})",
                bizExpId, versions.size(), keepCount);
            return 0;
        }

        int deleted = 0;
        // 删除超出保留数量的旧版本（跳过当前活跃版本）
        for (int i = keepCount; i < versions.size(); i++) {
            String version = versions.get(i);

            // 不删除活跃版本
            List<Bucket> activeVariants = bucketMapper.selectByExpIdAndVersion(bizExpId, version);
            boolean isActive = activeVariants.stream().anyMatch(Bucket::getIsActive);

            if (!isActive) {
                bucketMapper.deleteByVersion(bizExpId, version);
                deleted++;
                log.info("Deleted old version {} for experiment {}", version, bizExpId);
            }
        }

        log.info("Cleaned up {} old versions for experiment {}", deleted, bizExpId);
        return deleted;
    }

    /**
     * 验证分桶配置（仅验证variant_key唯一性）
     */
    private void validateVariantKeys(List<Bucket> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new VictorException(ErrorCode.VARIANT_EMPTY_LIST);
        }

        // 检查variant_key唯一性
        long distinctKeys = variants.stream()
            .map(Bucket::getBucketId)
            .distinct()
            .count();

        if (distinctKeys != variants.size()) {
            throw new VictorException(ErrorCode.VARIANT_DUPLICATE_KEY);
        }
    }

    /**
     * 自动重新分配分桶边界
     *
     * 策略：
     * 1. 将所有分桶平均分配 [0, 9999] 的范围
     * 2. 确保无重叠、无间隙
     * 3. 最后一个分桶的bucketEnd固定为9999
     *
     * 例如：
     * - 2个分桶: control[0-4999], treatment[5000-9999]
     * - 3个分桶: control[0-3332], treatment_a[3333-6665], treatment_b[6666-9999]
     * - 4个分桶: control[0-2499], treatment_a[2500-4999], treatment_b[5000-7499], treatment_c[7500-9999]
     */
    private void redistributeBucketRanges(List<Bucket> variants) {
        int totalBuckets = 10000; // [0, 9999]
        int variantCount = variants.size();

        if (variantCount == 0) {
            throw new VictorException(ErrorCode.VARIANT_EMPTY_LIST);
        }

        // 计算每个分桶的基础大小
        int baseSize = totalBuckets / variantCount;
        int remainder = totalBuckets % variantCount;

        int currentStart = 0;
        for (int i = 0; i < variantCount; i++) {
            Bucket variant = variants.get(i);

            // 前 remainder 个分桶各多分配1个单位
            int size = baseSize + (i < remainder ? 1 : 0);
            int currentEnd = currentStart + size - 1;

            // 最后一个分桶固定结束于9999
            if (i == variantCount - 1) {
                currentEnd = 9999;
            }

            variant.setBucketStart(currentStart);
            variant.setBucketEnd(currentEnd);

            log.debug("Assigned bucket range for {}: [{}, {}]",
                variant.getBucketId(), currentStart, currentEnd);

            currentStart = currentEnd + 1;
        }

        // 验证最后一个分桶的结束位置
        Bucket lastVariant = variants.get(variantCount - 1);
        if (lastVariant.getBucketEnd() != 9999) {
            throw new VictorException(ErrorCode.BKT_LAST_END_MUST_9999, String.valueOf(lastVariant.getBucketEnd()));
        }

        log.info("Redistributed bucket ranges for {} variants: {}", variantCount,
            variants.stream().map(v -> v.getBucketId() + "[" + v.getBucketStart() + "-" + v.getBucketEnd() + "]")
                .collect(Collectors.joining(", ")));
    }

    /**
     * 版本对比结果DTO
     */
    public static class VersionComparison {
        private String version1;
        private String version2;
        private List<Bucket> variants1;
        private List<Bucket> variants2;
        private boolean hasDifferences;
        private int variantCount1;
        private int variantCount2;

        public String getVersion1() { return version1; }
        public void setVersion1(String version1) { this.version1 = version1; }

        public String getVersion2() { return version2; }
        public void setVersion2(String version2) { this.version2 = version2; }

        public List<Bucket> getVariants1() { return variants1; }
        public void setVariants1(List<Bucket> variants1) { this.variants1 = variants1; }

        public List<Bucket> getVariants2() { return variants2; }
        public void setVariants2(List<Bucket> variants2) { this.variants2 = variants2; }

        public boolean isHasDifferences() { return hasDifferences; }
        public void setHasDifferences(boolean hasDifferences) { this.hasDifferences = hasDifferences; }

        public int getVariantCount1() { return variantCount1; }
        public void setVariantCount1(int variantCount1) { this.variantCount1 = variantCount1; }

        public int getVariantCount2() { return variantCount2; }
        public void setVariantCount2(int variantCount2) { this.variantCount2 = variantCount2; }
    }
}
