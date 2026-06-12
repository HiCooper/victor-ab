package com.gateflow.victor.service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.common.enums.ExperimentStatus;
import com.gateflow.victor.domain.dto.ConfigResponse;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.ConfigVersion;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.infra.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

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
    private static final long CACHE_TTL_SECONDS = 300; // 5 minutes
    private static final long LOCK_TTL_SECONDS = 10;   // 10 seconds mutex lock
    private final ExperimentMapper experimentMapper;
    private final LayerMapper layerMapper;
    private final BucketMapper bucketMapper;
    private final DomainMapper domainMapper;
    private final ConfigVersionMapper configVersionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 查询最新配置版本（Redis 缓存 + 互斥锁防击穿）
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

                // 查数据库
                ConfigVersion latest = configVersionMapper.selectLatestVersion();
                if (latest != null) {
                    redisTemplate.opsForValue().set(REDIS_KEY_LATEST, latest.getVersion(),
                            Duration.ofSeconds(CACHE_TTL_SECONDS));
                    return new VersionInfo(latest.getVersion(),
                            latest.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000);
                }

                // 无版本记录，生成当前版本并缓存
                String currentVersion = generateCurrentVersion();
                redisTemplate.opsForValue().set(REDIS_KEY_LATEST, currentVersion,
                        Duration.ofSeconds(CACHE_TTL_SECONDS));
                return new VersionInfo(currentVersion, System.currentTimeMillis());
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
            // 最终降级：直接查数据库
            ConfigVersion latest = configVersionMapper.selectLatestVersion();
            if (latest != null) {
                return new VersionInfo(latest.getVersion(),
                        latest.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000);
            }
            String currentVersion = generateCurrentVersion();
            return new VersionInfo(currentVersion, System.currentTimeMillis());
        }
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

        // 查询关联数据
        Map<Long, Layer> layerMap = new HashMap<>();
        Map<String, List<Bucket>> bucketMap = new HashMap<>();

        for (Experiment exp : experiments) {
            Layer layer = layerMapper.selectById(exp.getLayerId());
            if (layer != null) {
                layerMap.put(exp.getLayerId(), layer);
            }
            bucketMap.put(exp.getExpId(), bucketMapper.selectByExpId(exp.getExpId()));
        }

        // 构建配置
        List<ConfigResponse.ExperimentConfig> expConfigs = experiments.stream()
                .map(exp -> buildExperimentConfig(exp, layerMap, bucketMap))
                .toList();

        ConfigResponse response = new ConfigResponse();
        response.setVersion(generateCurrentVersion());
        response.setChangeType("FULL");
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
        config.setBucketStart(0);
        config.setBucketEnd(9999);

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
     * 生成当前版本号
     */
    private String generateCurrentVersion() {
        return java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    /**
     * 判断实验是否可分桶
     */
    private boolean isBucketable(String status) {
        ExperimentStatus s = ExperimentStatus.fromCode(status);
        return s != null && s.isBucketable();
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