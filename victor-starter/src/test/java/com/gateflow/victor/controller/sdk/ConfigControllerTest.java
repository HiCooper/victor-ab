package com.gateflow.victor.controller.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.domain.dto.ConfigResponse;
import com.gateflow.victor.service.config.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ConfigController 集成测试
 */
@WebMvcTest(ConfigController.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConfigService configService;

    private ConfigResponse testConfigResponse;

    @BeforeEach
    void setUp() {
        testConfigResponse = new ConfigResponse();
        testConfigResponse.setVersion("20240505-120000");
        testConfigResponse.setChangeType("FULL");
        testConfigResponse.setEtag("etag_001");

        ConfigResponse.ExperimentConfig expConfig = new ConfigResponse.ExperimentConfig();
        expConfig.setExpId("exp_test_001");
        expConfig.setLayerId("layer_ui");
        expConfig.setSalt("ui_salt");
        expConfig.setBucketStart(0);
        expConfig.setBucketEnd(999);

        ConfigResponse.BucketConfig bucketConfig = new ConfigResponse.BucketConfig();
        bucketConfig.setBucketId("control");
        bucketConfig.setBucketStart(0);
        bucketConfig.setBucketEnd(499);
        Map<String, Object> params = new HashMap<>();
        params.put("color", "blue");
        bucketConfig.setParams(params);

        expConfig.setBuckets(List.of(bucketConfig));
        testConfigResponse.setExperiments(List.of(expConfig));
    }

    @Test
    @DisplayName("查询配置版本 - 有更新")
    void checkVersion_HasUpdate() throws Exception {
        ConfigService.VersionInfo versionInfo = new ConfigService.VersionInfo("20240505-120000", 1715040000000L);
        when(configService.getLatestVersion()).thenReturn(versionInfo);

        mockMvc.perform(get("/api/v1/config/version")
                        .param("platform", "server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("20240505-120000"));

        verify(configService).getLatestVersion();
    }

    @Test
    @DisplayName("查询配置版本 - 无更新(版本一致)")
    void checkVersion_NoUpdate() throws Exception {
        ConfigService.VersionInfo versionInfo = new ConfigService.VersionInfo("20240505-120000", 1715040000000L);
        when(configService.getLatestVersion()).thenReturn(versionInfo);

        mockMvc.perform(get("/api/v1/config/version")
                        .param("platform", "server")
                        .param("version", "20240505-120000"))
                .andExpect(status().isNotModified());

        verify(configService).getLatestVersion();
    }

    @Test
    @DisplayName("拉取配置 - 全量拉取")
    void fetchConfig_Full() throws Exception {
        when(configService.getFullConfig("server")).thenReturn(testConfigResponse);

        mockMvc.perform(get("/api/v1/config/fetch")
                        .param("platform", "server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("20240505-120000"))
                .andExpect(jsonPath("$.changeType").value("FULL"))
                .andExpect(jsonPath("$.experiments[0].expId").value("exp_test_001"));

        verify(configService).getFullConfig("server");
    }

    @Test
    @DisplayName("拉取配置 - 增量拉取")
    void fetchConfig_Incremental() throws Exception {
        ConfigResponse incrementalResponse = new ConfigResponse();
        incrementalResponse.setVersion("20240505-130000");
        incrementalResponse.setChangeType("INCREMENTAL");

        when(configService.getIncrementalConfig("20240505-120000", "server"))
                .thenReturn(incrementalResponse);

        mockMvc.perform(get("/api/v1/config/fetch")
                        .param("platform", "server")
                        .param("fromVersion", "20240505-120000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeType").value("INCREMENTAL"));

        verify(configService).getIncrementalConfig("20240505-120000", "server");
    }

    @Test
    @DisplayName("拉取配置 - 无实验")
    void fetchConfig_EmptyExperiments() throws Exception {
        ConfigResponse emptyResponse = new ConfigResponse();
        emptyResponse.setVersion("20240505-120000");
        emptyResponse.setChangeType("FULL");
        emptyResponse.setExperiments(Collections.emptyList());

        when(configService.getFullConfig("server")).thenReturn(emptyResponse);

        mockMvc.perform(get("/api/v1/config/fetch")
                        .param("platform", "server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.experiments").isEmpty());

        verify(configService).getFullConfig("server");
    }
}