package com.gateflow.victor.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.bucketing.BucketEngine;
import com.gateflow.victor.bucketing.BucketResult;
import com.gateflow.victor.sdk.model.SdkConfigResponse;
import com.gateflow.victor.sdk.model.SdkExperimentTag;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Victor SDK 客户端 - 主入口类
 * 
 * 供其他Java服务集成，提供分桶计算、配置拉取等功能
 * 
 * 特性:
 * - 本地 Caffeine 缓存
 * - 定时轮询配置更新
 * - 离线容灾: 网络异常时使用本地持久化缓存
 */
public class VictorClient {

    private static final Logger LOGGER = Logger.getLogger(VictorClient.class.getName());
    private static final String CACHE_FILENAME = "victor_sdk_cache.json";

    private final VictorConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Cache<String, SdkConfigResponse.ExperimentConfig> experimentCache;
    private final ScheduledExecutorService scheduler;
    private final ReentrantLock configLock = new ReentrantLock();
    private final Path cacheFilePath;

    private volatile String currentVersion;
    private volatile SdkConfigResponse fallbackConfig; // 离线容灾配置
    private volatile boolean initialized = false;

    private VictorClient(VictorConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getRequestTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getRequestTimeout(), TimeUnit.MILLISECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        
        this.experimentCache = Caffeine.newBuilder()
                .maximumSize(config.getCacheMaxSize())
                .expireAfterWrite(config.getCacheExpiry(), TimeUnit.SECONDS)
                .build();

        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        // 初始化离线缓存文件路径
        String cacheDir = config.getCacheDir() != null ? config.getCacheDir() : System.getProperty("user.home") + "/.victor";
        this.cacheFilePath = Paths.get(cacheDir, CACHE_FILENAME);
    }

    /**
     * 初始化SDK
     */
    public static VictorClient init(VictorConfig config) {
        VictorClient client = new VictorClient(config);
        client.initialize();
        return client;
    }

    /**
     * 初始化 - 加载离线缓存并拉取配置
     */
    private void initialize() {
        // 1. 先加载本地离线缓存
        loadLocalCache();

        // 2. 尝试拉取远程配置
        fetchConfig();

        // 3. 启动定时轮询
        scheduler.scheduleAtFixedRate(
                this::checkAndUpdateConfig,
                config.getPollingInterval(),
                config.getPollingInterval(),
                TimeUnit.SECONDS
        );

        initialized = true;
        LOGGER.info("VictorClient initialized with version: " + currentVersion);
    }

    /**
     * 加载本地离线缓存
     */
    private void loadLocalCache() {
        try {
            if (Files.exists(cacheFilePath)) {
                String json = new String(Files.readAllBytes(cacheFilePath));
                fallbackConfig = objectMapper.readValue(json, SdkConfigResponse.class);
                
                // 恢复缓存到内存
                if (fallbackConfig.getExperiments() != null) {
                    for (SdkConfigResponse.ExperimentConfig expConfig : fallbackConfig.getExperiments()) {
                        experimentCache.put(expConfig.getExpId(), expConfig);
                    }
                }
                currentVersion = fallbackConfig.getVersion();
                
                LOGGER.info("Loaded local cache with " + fallbackConfig.getExperiments().size() + " experiments");
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load local cache: " + e.getMessage());
        }
    }

    /**
     * 持久化配置到本地（离线容灾）
     */
    private void persistConfig(SdkConfigResponse configResponse) {
        try {
            // 确保目录存在
            if (!Files.exists(cacheFilePath.getParent())) {
                Files.createDirectories(cacheFilePath.getParent());
            }
            
            String json = objectMapper.writeValueAsString(configResponse);
            Files.write(cacheFilePath, json.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            LOGGER.fine("Persisted config to local cache");
        } catch (IOException e) {
            LOGGER.warning("Failed to persist config: " + e.getMessage());
        }
    }

    /**
     * 获取用户在指定实验中的版本
     *
     * @param userId        用户ID
     * @param experimentKey 实验标识
     * @return 版本标识 (null表示未命中)
     */
    public String getVariant(String userId, String experimentKey) {
        SdkConfigResponse.ExperimentConfig expConfig = experimentCache.getIfPresent(experimentKey);
        if (expConfig == null) {
            LOGGER.fine("Experiment config not found: " + experimentKey);
            return null;
        }

        // 本地计算分桶
        BucketEngine.ExperimentSpec spec = buildExperimentSpec(expConfig);
        BucketResult result = BucketEngine.computeBucketResult(userId, spec);

        return result.isHit() ? result.getVariant() : null;
    }

    /**
     * 获取用户所有实验的分桶结果
     *
     * @param userId 用户ID
     * @return Map<expId, variant>
     */
    public Map<String, String> getAllVariants(String userId) {
        Map<String, SdkConfigResponse.ExperimentConfig> allConfigs = experimentCache.asMap();
        if (allConfigs.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> results = new HashMap<>();
        for (Map.Entry<String, SdkConfigResponse.ExperimentConfig> entry : allConfigs.entrySet()) {
            BucketEngine.ExperimentSpec spec = buildExperimentSpec(entry.getValue());
            BucketResult result = BucketEngine.computeBucketResult(userId, spec);
            if (result.isHit()) {
                results.put(entry.getKey(), result.getVariant());
            }
        }

        return results;
    }

    /**
     * 获取实验参数
     *
     * @param userId        用户ID
     * @param experimentKey 实验标识
     * @param paramKey      参数名
     * @param defaultValue  默认值
     * @return 参数值
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String userId, String experimentKey, String paramKey, T defaultValue) {
        String variant = getVariant(userId, experimentKey);
        if (variant == null) {
            return defaultValue;
        }

        SdkConfigResponse.ExperimentConfig expConfig = experimentCache.getIfPresent(experimentKey);
        if (expConfig == null || expConfig.getVariants() == null) {
            return defaultValue;
        }

        for (SdkConfigResponse.VariantConfig vc : expConfig.getVariants()) {
            if (vc.getVariantKey().equals(variant) && vc.getParams() != null) {
                Object value = vc.getParams().get(paramKey);
                if (value != null) {
                    return (T) value;
                }
            }
        }

        return defaultValue;
    }

    /**
     * 获取实验标签 (用于埋点)
     *
     * @param userId 用户ID
     * @return 实验标签列表
     */
    public List<SdkExperimentTag> getExperimentTags(String userId) {
        Map<String, String> variants = getAllVariants(userId);
        List<SdkExperimentTag> tags = new ArrayList<>();

        for (Map.Entry<String, String> entry : variants.entrySet()) {
            SdkConfigResponse.ExperimentConfig expConfig = experimentCache.getIfPresent(entry.getKey());
            if (expConfig != null) {
                SdkExperimentTag tag = new SdkExperimentTag();
                tag.setExpId(entry.getKey());
                tag.setVariant(entry.getValue());
                tag.setLayer(expConfig.getLayerId());
                tags.add(tag);
            }
        }

        return tags;
    }

    /**
     * 拉取配置（带离线容灾）
     */
    private void fetchConfig() {
        try {
            String url = config.getServerUrl() + "/api/v1/config/fetch?platform=" + config.getPlatform();
            if (currentVersion != null) {
                url += "&fromVersion=" + currentVersion;
            }

            Request request = new Request.Builder()
                    .url(url)
                    .header("X-API-Key", config.getApiKey() != null ? config.getApiKey() : "")
                    .get()
                    .build();

            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful() && response.body() != null) {
                String json = response.body().string();
                SdkConfigResponse configResponse = objectMapper.readValue(json, SdkConfigResponse.class);
                updateConfig(configResponse);
                
                // 成功拉取后持久化到本地
                persistConfig(configResponse);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to fetch config, using local fallback: " + e.getMessage());
            if (fallbackConfig != null) {
                LOGGER.info("Using offline fallback config");
            } else {
                LOGGER.severe("No local cache available, SDK will not function until network is restored");
            }
        }
    }

    /**
     * 检查并更新配置
     */
    private void checkAndUpdateConfig() {
        try {
            String url = config.getServerUrl() + "/api/v1/config/version?platform=" + config.getPlatform();
            if (currentVersion != null) {
                url += "&version=" + currentVersion;
            }

            Request request = new Request.Builder()
                    .url(url)
                    .header("X-API-Key", config.getApiKey() != null ? config.getApiKey() : "")
                    .get()
                    .build();

            Response response = httpClient.newCall(request).execute();
            if (response.code() == 200 && response.body() != null) {
                // 有更新，拉取增量配置
                fetchConfig();
            }
            // 304表示无更新，无需处理
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check config version, using cached config: " + e.getMessage());
        }
    }

    /**
     * 更新本地配置
     */
    private void updateConfig(SdkConfigResponse configResponse) {
        configLock.lock();
        try {
            currentVersion = configResponse.getVersion();
            fallbackConfig = configResponse; // 更新离线容灾配置

            if (configResponse.getExperiments() != null) {
                for (SdkConfigResponse.ExperimentConfig expConfig : configResponse.getExperiments()) {
                    experimentCache.put(expConfig.getExpId(), expConfig);
                }
            }

            // 处理删除的实验
            if (configResponse.getDeletedExperimentIds() != null) {
                for (String expId : configResponse.getDeletedExperimentIds()) {
                    experimentCache.invalidate(expId);
                }
            }
        } finally {
            configLock.unlock();
        }
    }

    /**
     * 构建实验规格
     */
    private BucketEngine.ExperimentSpec buildExperimentSpec(SdkConfigResponse.ExperimentConfig config) {
        List<BucketEngine.VariantSpec> variants = new ArrayList<>();
        if (config.getVariants() != null) {
            for (SdkConfigResponse.VariantConfig vc : config.getVariants()) {
                variants.add(new BucketEngine.VariantSpec(
                        vc.getVariantKey(),
                        vc.getBucketStart(),
                        vc.getBucketEnd()
                ));
            }
        }

        return new BucketEngine.ExperimentSpec(
                config.getExpId(),
                config.getLayerId(),
                config.getSalt(),
                config.getBucketStart(),
                config.getBucketEnd(),
                variants
        );
    }

    /**
     * 关闭SDK
     */
    public void shutdown() {
        scheduler.shutdown();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        
        // 关闭前持久化最新配置
        if (fallbackConfig != null) {
            persistConfig(fallbackConfig);
        }
    }

    /**
     * 获取当前配置版本
     */
    public String getCurrentVersion() {
        return currentVersion;
    }

    /**
     * 是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 是否使用离线模式
     */
    public boolean isOfflineMode() {
        return fallbackConfig != null && currentVersion == null;
    }
}
