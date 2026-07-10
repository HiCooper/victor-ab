package com.gateflow.victor.service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.common.util.MurmurHash3;
import com.gateflow.victor.domain.dto.ConfigResponse;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.infra.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 配置服务 - SDK配置拉取
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private static final String REDIS_KEY_PREFIX = "victor:config:";
    private static final String REDIS_KEY_LATEST = REDIS_KEY_PREFIX + "latest";
    private static final String REDIS_KEY_LOCK = REDIS_KEY_PREFIX + "lock:latest";
    private static final long CACHE_TTL_SECONDS = 30; // 版本缓存 TTL：配置变更最迟在此时间内被 SDK 感知
    private static final long LOCK_TTL_SECONDS = 10;   // 10 seconds mutex lock
    private final ExperimentMapper experimentMapper;
    private final LayerMapper layerMapper;
    private final BucketMapper bucketMapper;
    private final DomainMapper domainMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 查询最新配置版本（Redis 缓存 + 互斥锁防击穿）。
     * <p>
     * 版本由运行中配置内容派生（见 {@link #computeVersion}），与 {@link #getFullConfig}
     * 下发的版本一致，因此 SDK 仅在配置真正变化时才重新拉取全量；缓存 TTL 决定变更的最大感知延迟。
     *
     * @return 版本信息
     */
    public VersionInfo getLatestVersion() {
        // 先查 Redis 缓存
        String cachedVersion = redisTemplate.opsForValue().get(REDIS_KEY_LATEST);
        if (cachedVersion != null) {
            return new VersionInfo(cachedVersion, System.currentTimeMillis());
        }

        // 缓存未命中 — 尝试获取互斥锁防止缓存击穿
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(REDIS_KEY_LOCK, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                // 双重检查：获取锁后再次检查缓存
                cachedVersion = redisTemplate.opsForValue().get(REDIS_KEY_LATEST);
                if (cachedVersion != null) {
                    return new VersionInfo(cachedVersion, System.currentTimeMillis());
                }

                List<Experiment> running = experimentMapper.selectRunningExperiments();
                String version = computeVersion(running, layersFor(running));
                redisTemplate.opsForValue().set(REDIS_KEY_LATEST, version,
                        Duration.ofSeconds(CACHE_TTL_SECONDS));
                return new VersionInfo(version, System.currentTimeMillis());
            } finally {
                redisTemplate.delete(REDIS_KEY_LOCK);
            }
        } else {
            // 未获取锁，短暂等待后重试读取缓存
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cachedVersion = redisTemplate.opsForValue().get(REDIS_KEY_LATEST);
            if (cachedVersion != null) {
                return new VersionInfo(cachedVersion, System.currentTimeMillis());
            }
            // 最终降级：直接计算
            List<Experiment> running = experimentMapper.selectRunningExperiments();
            return new VersionInfo(
                    computeVersion(running, layersFor(running)),
                    System.currentTimeMillis());
        }
    }

    /**
     * 计算配置版本指纹：对所有运行中实验的 (expId, updatedAt) 及其所属层的 (layerId, updatedAt)
     * 排序后哈希。
     * <p>
     * 确定性（同一配置恒得同一版本），且随配置变更而变 —— 创建/编辑/启停实验、编辑变体都会更新
     * {@code experiment.updated_at}，实验集合增删改变实验部分；层的 salt/排序等变更会更新
     * {@code layer.updated_at}，改变层部分。因此 {@code /config/version} 与 {@code /config/fetch}
     * 返回的版本天然一致，且下发内容的任何变化（含层 salt）都会触发版本变化。
     */
    private String computeVersion(List<Experiment> runningExperiments, List<Layer> layers) {
        if (runningExperiments == null || runningExperiments.isEmpty()) {
            return "v0-empty";
        }
        String expPart = runningExperiments.stream()
                .sorted(Comparator.comparing(Experiment::getExpId))
                .map(e -> e.getExpId() + "@" + (e.getUpdatedAt() != null ? e.getUpdatedAt() : ""))
                .collect(Collectors.joining(","));
        String layerPart = (layers == null ? List.<Layer>of() : layers).stream()
                .filter(l -> l.getId() != null)
                .sorted(Comparator.comparing(Layer::getId))
                .map(l -> l.getId() + "@" + (l.getUpdatedAt() != null ? l.getUpdatedAt() : ""))
                .collect(Collectors.joining(","));
        return "v" + Long.toHexString(MurmurHash3.hash64(expPart + "|" + layerPart));
    }

    /** 查询运行中实验涉及的层（用于版本指纹与配置构建），保证 /version 与 /fetch 使用相同的层集合。 */
    private List<Layer> layersFor(List<Experiment> experiments) {
        if (experiments == null || experiments.isEmpty()) {
            return List.of();
        }
        List<Long> ids = experiments.stream()
                .map(Experiment::getLayerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Layer> layers = layerMapper.selectByIds(ids);
        return layers != null ? layers : List.of();
    }

    /**
     * 检查版本是否有更新
     *
     * @param clientVersion 客户端当前版本
     * @return true表示有更新
     */
    public boolean hasUpdate(String clientVersion) {
        VersionInfo latest = getLatestVersion();
        return !latest.getVersion().equals(clientVersion);
    }

    /**
     * 获取完整配置
     *
     * @param platform 平台
     * @return 配置响应
     */
    public ConfigResponse getFullConfig(String platform) {
        // 查询所有运行中的实验
        List<Experiment> experiments = experimentMapper.selectRunningExperiments();

        ConfigResponse response = new ConfigResponse();
        response.setChangeType("FULL");

        if (experiments.isEmpty()) {
            response.setVersion(computeVersion(experiments, List.of()));
            response.setExperiments(Collections.emptyList());
            return response;
        }

        // 批量查询关联数据，避免 N+1；层集合与 /config/version 一致，用于派生版本
        List<Layer> layers = layersFor(experiments);
        // 版本与 /config/version 一致，由实验 + 层内容派生
        response.setVersion(computeVersion(experiments, layers));
        Map<Long, Layer> layerMap = layers.stream()
                .collect(Collectors.toMap(Layer::getId, l -> l));

        List<String> expIds = experiments.stream()
                .map(Experiment::getExpId)
                .distinct()
                .toList();
        Map<String, List<Bucket>> bucketMap = bucketMapper.selectActiveBucketsByExpIds(expIds).stream()
                .collect(Collectors.groupingBy(Bucket::getExpId));

        List<ConfigResponse.ExperimentConfig> expConfigs = experiments.stream()
                .map(exp -> buildExperimentConfig(exp, layerMap, bucketMap))
                .toList();
        response.setExperiments(expConfigs);

        return response;
    }

    /**
     * 获取增量配置
     *
     * @param fromVersion 起始版本
     * @param platform    平台
     * @return 配置响应
     */
    public ConfigResponse getIncrementalConfig(String fromVersion, String platform) {
        // Incremental mode is deprecated — always return a full config pull.
        // The AB config payload is small enough that incremental diff adds complexity
        // without meaningful bandwidth savings.
        log.debug("Incremental config requested (fromVersion={}), falling back to full pull", fromVersion);
        ConfigResponse response = getFullConfig(platform);
        response.setChangeType("FULL");
        return response;
    }

    /**
     * 构建实验配置
     */
    private ConfigResponse.ExperimentConfig buildExperimentConfig(
            Experiment exp, Map<Long, Layer> layerMap, Map<String, List<Bucket>> bucketMap) {

        Layer layer = layerMap.get(exp.getLayerId());
        List<Bucket> buckets = bucketMap.getOrDefault(exp.getExpId(), Collections.emptyList());

        ConfigResponse.ExperimentConfig config = new ConfigResponse.ExperimentConfig();
        config.setExpId(exp.getExpId());
        // 下发实验在所属层内的真实桶段，SDK 据此本地判定命中（层内互斥）
        config.setBucketStart(exp.getBucketStart() != null ? exp.getBucketStart() : 0);
        config.setBucketEnd(exp.getBucketEnd() != null ? exp.getBucketEnd() : 9999);

        if (layer != null) {
            config.setLayerId(layer.getLayerId());
            config.setSalt(layer.getSalt());
        }

        List<ConfigResponse.BucketConfig> bucketConfigs = buckets.stream()
                .map(v -> {
                    ConfigResponse.BucketConfig vc = new ConfigResponse.BucketConfig();
                    vc.setBucketId(v.getBucketId());
                    vc.setBucketStart(v.getBucketStart());
                    vc.setBucketEnd(v.getBucketEnd());
                    vc.setParams(parseParams(v.getParams()));
                    return vc;
                })
                .toList();

        config.setBuckets(bucketConfigs);

        return config;
    }

    /**
     * 解析参数JSON
     */
    private Map<String, Object> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(paramsJson, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse params JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 版本信息
     */
    public static class VersionInfo {
        private final String version;
        private final long timestamp;

        public VersionInfo(String version, long timestamp) {
            this.version = version;
            this.timestamp = timestamp;
        }

        public String getVersion() {
            return version;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}