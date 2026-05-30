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
    private final ExperimentMapper experimentMapper;
    private final LayerMapper layerMapper;
    private final BucketMapper bucketMapper;
    private final DomainMapper domainMapper;
    private final ConfigVersionMapper configVersionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 查询最新配置版本
     *
     * @return 版本信息
     */
    public VersionInfo getLatestVersion() {
        // 先查Redis
        String cachedVersion = redisTemplate.opsForValue().get(REDIS_KEY_LATEST);
        if (cachedVersion != null) {
            return new VersionInfo(cachedVersion, System.currentTimeMillis());
        }

        // 查数据库
        ConfigVersion latest = configVersionMapper.selectLatestVersion();
        if (latest != null) {
            // 更新Redis缓存
            redisTemplate.opsForValue().set(REDIS_KEY_LATEST, latest.getVersion());
            return new VersionInfo(latest.getVersion(), latest.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000);
        }

        // 无版本记录，生成当前配置版本
        String currentVersion = generateCurrentVersion();
        return new VersionInfo(currentVersion, System.currentTimeMillis());
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
        // 查询版本变更记录
        List<ConfigVersion> changes = configVersionMapper.selectChangesAfterVersion(fromVersion);

        if (changes.isEmpty()) {
            // 无变更，返回空响应
            ConfigResponse response = new ConfigResponse();
            response.setVersion(fromVersion);
            response.setChangeType("INCREMENTAL");
            response.setExperiments(Collections.emptyList());
            return response;
        }

        // 获取最新版本
        ConfigVersion latest = changes.get(changes.size() - 1);

        // 解析变更的实验
        Set<String> changedExpIds = parseChangedExperiments(latest);

        // 查询变更的实验
        List<Experiment> experiments = new ArrayList<>();
        for (String expId : changedExpIds) {
            Experiment exp = experimentMapper.selectByExpId(expId);
            if (exp != null && isBucketable(exp.getStatus())) {
                experiments.add(exp);
            }
        }

        // 构建配置
        Map<Long, Layer> layerMap = new HashMap<>();
        Map<String, List<Bucket>> bucketMap = new HashMap<>();

        for (Experiment exp : experiments) {
            Layer layer = layerMapper.selectById(exp.getLayerId());
            if (layer != null) {
                layerMap.put(exp.getLayerId(), layer);
            }
            bucketMap.put(exp.getExpId(), bucketMapper.selectByExpId(exp.getExpId()));
        }

        List<ConfigResponse.ExperimentConfig> expConfigs = experiments.stream()
                .map(exp -> buildExperimentConfig(exp, layerMap, bucketMap))
                .toList();

        ConfigResponse response = new ConfigResponse();
        response.setVersion(latest.getVersion());
        response.setChangeType("INCREMENTAL");
        response.setExperiments(expConfigs);

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
     * 解析变更的实验ID
     */
    private Set<String> parseChangedExperiments(ConfigVersion configVersion) {
        if (configVersion.getChangedExperiments() == null) {
            return Collections.emptySet();
        }
        try {
            return objectMapper.readValue(configVersion.getChangedExperiments(), Set.class);
        } catch (JsonProcessingException e) {
            return Collections.emptySet();
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