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
     * @param newBuckets 新的分桶配置列表
     * @return 新版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public String createNewVersion(Long expId, List<Bucket> newBuckets) {
        if (newBuckets == null || newBuckets.isEmpty()) {
            throw new VictorException(ErrorCode.VARIANT_EMPTY_LIST);
        }

        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }
        String bizExpId = experiment.getExpId();

        // 1. 验证分桶配置（仅验证bucket_key唯一性）
        validateBucketKeys(newBuckets);

        // 2. 自动重新分配分桶边界（确保无重叠、无间隙）
        // 注意：仅在 Bucket 没有预设 bucketStart/bucketEnd 时才重新分配
        // 如果前端已通过 trafficPercentage 指定了比例（由 ExperimentService.calculateBucketBoundaries 计算），则跳过
        boolean hasPredefinedBoundaries = newBuckets.stream()
            .anyMatch(v -> v.getBucketStart() != null && v.getBucketEnd() != null);
        
        if (!hasPredefinedBoundaries) {
            redistributeBucketRanges(newBuckets);
            log.info("Auto-redistributed bucket ranges for {} buckets", newBuckets.size());
        } else {
            log.info("Using predefined bucket boundaries for {} buckets", newBuckets.size());
        }

        // 3. 将当前版本标记为非活跃
        bucketMapper.deactivateAllBuckets(bizExpId);
        log.info("Deactivated all buckets for experiment {}", bizExpId);

        // 4. 生成新版本号
        String newVersion = generateVersion();
        LocalDateTime now = LocalDateTime.now();

        // 5. 插入新版本的分桶
        for (Bucket bucket : newBuckets) {
            bucket.setId(null); // 清除ID，作为新记录插入
            bucket.setExpId(bizExpId);
            bucket.setVersion(newVersion);
            bucket.setIsActive(true);
            bucket.setCreatedAt(now);
            bucketMapper.insert(bucket);
        }

        log.info("Created new version {} for experiment {} with {} buckets, bucket ranges: {}",
            newVersion, bizExpId, newBuckets.size(),
            newBuckets.stream().map(v -> v.getBucketId() + "[" + v.getBucketStart() + "-" + v.getBucketEnd() + "]")
                .collect(Collectors.joining(", ")));

        return newVersion;
    }

    /**
     * 获取实验的当前活跃版本
     *
     * @param expId 实验主键ID
     * @return 活跃版本列表
     */
    public List<Bucket> getActiveBuckets(Long expId) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            return List.of();
        }
        List<Bucket> buckets = bucketMapper.selectActiveBuckets(experiment.getExpId());
        if (buckets.isEmpty()) {
            log.warn("No active buckets found for experiment {}", expId);
        }
        return buckets;
    }

    /**
     * 获取实验的所有版本列表
     *
     * @param expId 实验主键ID
     * @return 所有版本列表
     */
    public List<Bucket> getAllBuckets(Long expId) {
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
    public List<Bucket> getBucketsByVersion(Long expId, String version) {
        Experiment experiment = experimentMapper.selectById(expId);
        if (experiment == null) {
            throw new VictorException(ErrorCode.EXP_NOT_FOUND, String.valueOf(expId));
        }
        List<Bucket> buckets = bucketMapper.selectByExpIdAndVersion(experiment.getExpId(), version);
        if (buckets.isEmpty()) {
            throw new VictorException(ErrorCode.VER_NOT_FOUND, String.valueOf(version));
        }
        return buckets;
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
        List<Bucket> targetBuckets = bucketMapper.selectByExpIdAndVersion(bizExpId, targetVersion);
        if (targetBuckets.isEmpty()) {
            throw new VictorException(ErrorCode.VER_NOT_FOUND, String.valueOf(targetVersion));
        }

        // 2. 将当前版本标记为非活跃
        bucketMapper.deactivateAllBuckets(bizExpId);
        log.info("Deactivated all buckets for experiment {} before rollback", bizExpId);

        // 3. 激活目标版本
        int activated = bucketMapper.activateVersion(bizExpId, targetVersion);
        if (activated == 0) {
            throw new VictorException(ErrorCode.VER_ACTIVATE_FAILED, String.valueOf(targetVersion));
        }

        log.info("Rolled back experiment {} to version {}", bizExpId, targetVersion);

        return targetBuckets;
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

        List<Bucket> buckets1 = bucketMapper.selectByExpIdAndVersion(bizExpId, version1);
        List<Bucket> buckets2 = bucketMapper.selectByExpIdAndVersion(bizExpId, version2);

        if (buckets1.isEmpty()) {
            throw new VictorException("Version not found: " + version1);
        }
        if (buckets2.isEmpty()) {
            throw new VictorException("Version not found: " + version2);
        }

        VersionComparison comparison = new VersionComparison();
        comparison.setVersion1(version1);
        comparison.setVersion2(version2);
        comparison.setBuckets1(buckets1);
        comparison.setBuckets2(buckets2);

        // 计算差异
        comparison.setHasDifferences(!buckets1.equals(buckets2));
        comparison.setBucketCount1(buckets1.size());
        comparison.setBucketCount2(buckets2.size());

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
            List<Bucket> activeBuckets = bucketMapper.selectByExpIdAndVersion(bizExpId, version);
            boolean isActive = activeBuckets.stream().anyMatch(Bucket::getIsActive);

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
     * 验证分桶配置（仅验证bucket_key唯一性）
     */
    private void validateBucketKeys(List<Bucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            throw new VictorException(ErrorCode.VARIANT_EMPTY_LIST);
        }

        // 检查bucket_key唯一性
        long distinctKeys = buckets.stream()
            .map(Bucket::getBucketId)
            .distinct()
            .count();

        if (distinctKeys != buckets.size()) {
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
    private void redistributeBucketRanges(List<Bucket> buckets) {
        int totalBuckets = 10000; // [0, 9999]
        int bucketCount = buckets.size();

        if (bucketCount == 0) {
            throw new VictorException(ErrorCode.VARIANT_EMPTY_LIST);
        }

        // 计算每个分桶的基础大小
        int baseSize = totalBuckets / bucketCount;
        int remainder = totalBuckets % bucketCount;

        int currentStart = 0;
        for (int i = 0; i < bucketCount; i++) {
            Bucket bucket = buckets.get(i);

            // 前 remainder 个分桶各多分配1个单位
            int size = baseSize + (i < remainder ? 1 : 0);
            int currentEnd = currentStart + size - 1;

            // 最后一个分桶固定结束于9999
            if (i == bucketCount - 1) {
                currentEnd = 9999;
            }

            bucket.setBucketStart(currentStart);
            bucket.setBucketEnd(currentEnd);

            log.debug("Assigned bucket range for {}: [{}, {}]",
                bucket.getBucketId(), currentStart, currentEnd);

            currentStart = currentEnd + 1;
        }

        // 验证最后一个分桶的结束位置
        Bucket lastBucket = buckets.get(bucketCount - 1);
        if (lastBucket.getBucketEnd() != 9999) {
            throw new VictorException(ErrorCode.BKT_LAST_END_MUST_9999, String.valueOf(lastBucket.getBucketEnd()));
        }

        log.info("Redistributed bucket ranges for {} buckets: {}", bucketCount,
            buckets.stream().map(v -> v.getBucketId() + "[" + v.getBucketStart() + "-" + v.getBucketEnd() + "]")
                .collect(Collectors.joining(", ")));
    }

    /**
     * 版本对比结果DTO
     */
    public static class VersionComparison {
        private String version1;
        private String version2;
        private List<Bucket> buckets1;
        private List<Bucket> buckets2;
        private boolean hasDifferences;
        private int bucketCount1;
        private int bucketCount2;

        public String getVersion1() { return version1; }
        public void setVersion1(String version1) { this.version1 = version1; }

        public String getVersion2() { return version2; }
        public void setVersion2(String version2) { this.version2 = version2; }

        public List<Bucket> getBuckets1() { return buckets1; }
        public void setBuckets1(List<Bucket> buckets1) { this.buckets1 = buckets1; }

        public List<Bucket> getBuckets2() { return buckets2; }
        public void setBuckets2(List<Bucket> buckets2) { this.buckets2 = buckets2; }

        public boolean isHasDifferences() { return hasDifferences; }
        public void setHasDifferences(boolean hasDifferences) { this.hasDifferences = hasDifferences; }

        public int getBucketCount1() { return bucketCount1; }
        public void setBucketCount1(int bucketCount1) { this.bucketCount1 = bucketCount1; }

        public int getBucketCount2() { return bucketCount2; }
        public void setBucketCount2(int bucketCount2) { this.bucketCount2 = bucketCount2; }
    }
}
