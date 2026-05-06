package com.gateflow.victor.service.experiment;

import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.infra.mapper.VariantMapper;
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
public class VariantVersionService {

    private final VariantMapper variantMapper;

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
     * @param expId 实验ID
     * @param newVariants 新的分桶配置列表
     * @return 新版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public String createNewVersion(Long expId, List<Variant> newVariants) {
        if (newVariants == null || newVariants.isEmpty()) {
            throw new VictorException("Variant list cannot be empty");
        }

        // 1. 验证分桶配置（仅验证variant_key唯一性）
        validateVariantKeys(newVariants);

        // 2. 自动重新分配分桶边界（确保无重叠、无间隙）
        redistributeBucketRanges(newVariants);

        // 3. 将当前版本标记为非活跃
        variantMapper.deactivateAllVariants(expId);
        log.info("Deactivated all variants for experiment {}", expId);

        // 4. 生成新版本号
        String newVersion = generateVersion();
        LocalDateTime now = LocalDateTime.now();

        // 5. 插入新版本的分桶
        for (Variant variant : newVariants) {
            variant.setId(null); // 清除ID，作为新记录插入
            variant.setExpId(expId);
            variant.setVersion(newVersion);
            variant.setIsActive(true);
            variant.setCreatedAt(now);
            variantMapper.insert(variant);
        }

        log.info("Created new version {} for experiment {} with {} variants, bucket ranges: {}", 
            newVersion, expId, newVariants.size(),
            newVariants.stream().map(v -> v.getVariantKey() + "[" + v.getBucketStart() + "-" + v.getBucketEnd() + "]")
                .collect(Collectors.joining(", ")));

        return newVersion;
    }

    /**
     * 获取实验的当前活跃版本
     * 
     * @param expId 实验ID
     * @return 活跃版本列表
     */
    public List<Variant> getActiveVariants(Long expId) {
        List<Variant> variants = variantMapper.selectActiveVariants(expId);
        if (variants.isEmpty()) {
            log.warn("No active variants found for experiment {}", expId);
        }
        return variants;
    }

    /**
     * 获取实验的所有版本列表
     * 
     * @param expId 实验ID
     * @return 所有版本列表
     */
    public List<Variant> getAllVariants(Long expId) {
        return variantMapper.selectByExpId(expId);
    }

    /**
     * 获取实验的指定版本
     * 
     * @param expId 实验ID
     * @param version 版本号
     * @return 版本列表
     */
    public List<Variant> getVariantsByVersion(Long expId, String version) {
        List<Variant> variants = variantMapper.selectByExpIdAndVersion(expId, version);
        if (variants.isEmpty()) {
            throw new VictorException("Version not found: " + version);
        }
        return variants;
    }

    /**
     * 获取实验的所有版本号列表
     * 
     * @param expId 实验ID
     * @return 版本号列表（按时间倒序）
     */
    public List<String> getVersionHistory(Long expId) {
        return variantMapper.selectVersionsByExpId(expId);
    }

    /**
     * 回滚到指定版本
     * 
     * 流程：
     * 1. 验证目标版本存在
     * 2. 将当前版本标记为非活跃
     * 3. 激活目标版本
     * 
     * @param expId 实验ID
     * @param targetVersion 目标版本号
     * @return 回滚后的版本列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Variant> rollbackToVersion(Long expId, String targetVersion) {
        // 1. 验证目标版本存在
        List<Variant> targetVariants = variantMapper.selectByExpIdAndVersion(expId, targetVersion);
        if (targetVariants.isEmpty()) {
            throw new VictorException("Version not found: " + targetVersion);
        }

        // 2. 将当前版本标记为非活跃
        variantMapper.deactivateAllVariants(expId);
        log.info("Deactivated all variants for experiment {} before rollback", expId);

        // 3. 激活目标版本
        int activated = variantMapper.activateVersion(expId, targetVersion);
        if (activated == 0) {
            throw new VictorException("Failed to activate version: " + targetVersion);
        }

        log.info("Rolled back experiment {} to version {}", expId, targetVersion);

        return targetVariants;
    }

    /**
     * 对比两个版本的差异
     * 
     * @param expId 实验ID
     * @param version1 版本1
     * @param version2 版本2
     * @return 版本对比结果
     */
    public VersionComparison compareVersions(Long expId, String version1, String version2) {
        List<Variant> variants1 = variantMapper.selectByExpIdAndVersion(expId, version1);
        List<Variant> variants2 = variantMapper.selectByExpIdAndVersion(expId, version2);

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
     * @param expId 实验ID
     * @param keepCount 保留的版本数量
     * @return 删除的版本数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupOldVersions(Long expId, int keepCount) {
        List<String> versions = variantMapper.selectVersionsByExpId(expId);
        
        if (versions.size() <= keepCount) {
            log.info("No old versions to cleanup for experiment {} (current: {}, keep: {})", 
                expId, versions.size(), keepCount);
            return 0;
        }

        int deleted = 0;
        // 删除超出保留数量的旧版本（跳过当前活跃版本）
        for (int i = keepCount; i < versions.size(); i++) {
            String version = versions.get(i);
            
            // 不删除活跃版本
            List<Variant> activeVariants = variantMapper.selectByExpIdAndVersion(expId, version);
            boolean isActive = activeVariants.stream().anyMatch(Variant::getIsActive);
            
            if (!isActive) {
                variantMapper.deleteByVersion(expId, version);
                deleted++;
                log.info("Deleted old version {} for experiment {}", version, expId);
            }
        }

        log.info("Cleaned up {} old versions for experiment {}", deleted, expId);
        return deleted;
    }

    /**
     * 验证分桶配置（仅验证variant_key唯一性）
     */
    private void validateVariantKeys(List<Variant> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new VictorException("Variant list cannot be empty");
        }

        // 检查variant_key唯一性
        long distinctKeys = variants.stream()
            .map(Variant::getVariantKey)
            .distinct()
            .count();
        
        if (distinctKeys != variants.size()) {
            throw new VictorException("Duplicate variant_key found");
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
    private void redistributeBucketRanges(List<Variant> variants) {
        int totalBuckets = 10000; // [0, 9999]
        int variantCount = variants.size();
        
        if (variantCount == 0) {
            throw new VictorException("Variant list cannot be empty");
        }

        // 计算每个分桶的基础大小
        int baseSize = totalBuckets / variantCount;
        int remainder = totalBuckets % variantCount;

        int currentStart = 0;
        for (int i = 0; i < variantCount; i++) {
            Variant variant = variants.get(i);
            
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
                variant.getVariantKey(), currentStart, currentEnd);

            currentStart = currentEnd + 1;
        }

        // 验证最后一个分桶的结束位置
        Variant lastVariant = variants.get(variantCount - 1);
        if (lastVariant.getBucketEnd() != 9999) {
            throw new VictorException("Last variant should end at 9999, but got: " + lastVariant.getBucketEnd());
        }

        log.info("Redistributed bucket ranges for {} variants: {}", variantCount,
            variants.stream().map(v -> v.getVariantKey() + "[" + v.getBucketStart() + "-" + v.getBucketEnd() + "]")
                .collect(Collectors.joining(", ")));
    }

    /**
     * 版本对比结果DTO
     */
    public static class VersionComparison {
        private String version1;
        private String version2;
        private List<Variant> variants1;
        private List<Variant> variants2;
        private boolean hasDifferences;
        private int variantCount1;
        private int variantCount2;

        public String getVersion1() { return version1; }
        public void setVersion1(String version1) { this.version1 = version1; }
        
        public String getVersion2() { return version2; }
        public void setVersion2(String version2) { this.version2 = version2; }
        
        public List<Variant> getVariants1() { return variants1; }
        public void setVariants1(List<Variant> variants1) { this.variants1 = variants1; }
        
        public List<Variant> getVariants2() { return variants2; }
        public void setVariants2(List<Variant> variants2) { this.variants2 = variants2; }
        
        public boolean isHasDifferences() { return hasDifferences; }
        public void setHasDifferences(boolean hasDifferences) { this.hasDifferences = hasDifferences; }
        
        public int getVariantCount1() { return variantCount1; }
        public void setVariantCount1(int variantCount1) { this.variantCount1 = variantCount1; }
        
        public int getVariantCount2() { return variantCount2; }
        public void setVariantCount2(int variantCount2) { this.variantCount2 = variantCount2; }
    }
}
