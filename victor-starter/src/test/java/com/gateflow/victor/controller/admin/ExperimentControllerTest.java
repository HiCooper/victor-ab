package com.gateflow.victor.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.domain.dto.ExperimentCreateRequest;
import com.gateflow.victor.domain.dto.ExperimentUpdateRequest;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.service.experiment.ExperimentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ExperimentController 集成测试
 */
@WebMvcTest(ExperimentController.class)
class ExperimentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ExperimentService experimentService;

    private Experiment testExperiment;
    private Bucket testBucket;

    @BeforeEach
    void setUp() {
        testExperiment = new Experiment();
        testExperiment.setId(1L);
        testExperiment.setExpId("exp_test_001");
        testExperiment.setName("测试实验");
        testExperiment.setDescription("测试实验描述");
        testExperiment.setLayerId(1L);
        testExperiment.setStatus("draft");

        testBucket = new Bucket();
        testBucket.setId(1L);
        testBucket.setExpId("exp_test_001");
        testBucket.setBucketId("control");
        testBucket.setName("对照组");
        testBucket.setBucketStart(0);
        testBucket.setBucketEnd(499);
    }

    @Test
    @DisplayName("创建实验 - 成功")
    void createExperiment_Success() throws Exception {
        // 准备请求
        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setExpId("exp_test_001");
        request.setName("测试实验");
        request.setDescription("测试实验描述");
        request.setLayerId(1L);

        ExperimentCreateRequest.BucketRequest bucketRequest = new ExperimentCreateRequest.BucketRequest();
        bucketRequest.setBucketKey("control");
        bucketRequest.setName("对照组");
        bucketRequest.setBucketStart(0);
        bucketRequest.setBucketEnd(499);
        request.setBuckets(List.of(bucketRequest));

        // Mock service
        when(experimentService.createExperiment(any(Experiment.class), anyList()))
                .thenReturn(testExperiment);

        // 执行请求
        mockMvc.perform(post("/api/v1/admin/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expId").value("exp_test_001"))
                .andExpect(jsonPath("$.name").value("测试实验"));

        verify(experimentService).createExperiment(any(Experiment.class), anyList());
    }

    @Test
    @DisplayName("创建实验 - 缺少必填字段")
    void createExperiment_MissingRequiredField() throws Exception {
        // 准备请求（缺少expId）
        ExperimentCreateRequest request = new ExperimentCreateRequest();
        request.setName("测试实验");
        request.setLayerId(1L);

        // 执行请求
        mockMvc.perform(post("/api/v1/admin/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(experimentService, never()).createExperiment(any(), any());
    }

    @Test
    @DisplayName("查询实验详情 - 成功")
    void getExperiment_Success() throws Exception {
        when(experimentService.getExperiment(1L)).thenReturn(testExperiment);

        mockMvc.perform(get("/api/v1/experiments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.expId").value("exp_test_001"));

        verify(experimentService).getExperiment(1L);
    }

    @Test
    @DisplayName("查询实验详情 - 不存在")
    void getExperiment_NotFound() throws Exception {
        when(experimentService.getExperiment(999L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/experiments/999"))
                .andExpect(status().isNotFound());

        verify(experimentService).getExperiment(999L);
    }

    @Test
    @DisplayName("根据业务标识查询实验 - 成功")
    void getExperimentByKey_Success() throws Exception {
        when(experimentService.getExperimentByKey("exp_test_001")).thenReturn(testExperiment);

        mockMvc.perform(get("/api/v1/experiments/key/exp_test_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expId").value("exp_test_001"));

        verify(experimentService).getExperimentByKey("exp_test_001");
    }

    @Test
    @DisplayName("根据业务标识查询实验 - 不存在")
    void getExperimentByKey_NotFound() throws Exception {
        when(experimentService.getExperimentByKey("not_exist")).thenReturn(null);

        mockMvc.perform(get("/api/v1/experiments/key/not_exist"))
                .andExpect(status().isNotFound());

        verify(experimentService).getExperimentByKey("not_exist");
    }

    @Test
    @DisplayName("查询实验列表 - 无筛选条件")
    void listExperiments_NoFilter() throws Exception {
        List<Experiment> experiments = List.of(testExperiment);
        when(experimentService.listExperiments(null, null)).thenReturn(experiments);

        mockMvc.perform(get("/api/v1/admin/experiments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expId").value("exp_test_001"));

        verify(experimentService).listExperiments(null, null);
    }

    @Test
    @DisplayName("查询实验列表 - 按层筛选")
    void listExperiments_FilterByLayer() throws Exception {
        List<Experiment> experiments = List.of(testExperiment);
        when(experimentService.listExperiments(1L, null)).thenReturn(experiments);

        mockMvc.perform(get("/api/v1/experiments?layerId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].layerId").value(1));

        verify(experimentService).listExperiments(1L, null);
    }

    @Test
    @DisplayName("查询实验列表 - 按状态筛选")
    void listExperiments_FilterByStatus() throws Exception {
        List<Experiment> experiments = List.of(testExperiment);
        when(experimentService.listExperiments(null, "draft")).thenReturn(experiments);

        mockMvc.perform(get("/api/v1/experiments?status=draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("draft"));

        verify(experimentService).listExperiments(null, "draft");
    }

    @Test
    @DisplayName("更新实验 - 成功")
    void updateExperiment_Success() throws Exception {
        ExperimentUpdateRequest request = new ExperimentUpdateRequest();
        request.setName("更新后的名称");
        request.setDescription("更新后的描述");

        Experiment updated = new Experiment();
        updated.setId(1L);
        updated.setName("更新后的名称");
        updated.setDescription("更新后的描述");

        when(experimentService.updateExperiment(any(Experiment.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/experiments/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("更新后的名称"));

        verify(experimentService).updateExperiment(any(Experiment.class));
    }

    @Test
    @DisplayName("启动实验 - 成功")
    void startExperiment_Success() throws Exception {
        Experiment running = new Experiment();
        running.setId(1L);
        running.setStatus("running");

        when(experimentService.startExperiment(1L)).thenReturn(running);

        mockMvc.perform(post("/api/v1/experiments/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("running"));

        verify(experimentService).startExperiment(1L);
    }

    @Test
    @DisplayName("停止实验 - 成功")
    void stopExperiment_Success() throws Exception {
        Experiment stopped = new Experiment();
        stopped.setId(1L);
        stopped.setStatus("paused");

        when(experimentService.stopExperiment(1L)).thenReturn(stopped);

        mockMvc.perform(post("/api/v1/experiments/1/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("paused"));

        verify(experimentService).stopExperiment(1L);
    }

    @Test
    @DisplayName("删除实验 - 成功")
    void deleteExperiment_Success() throws Exception {
        doNothing().when(experimentService).deleteExperiment(1L);

        mockMvc.perform(delete("/api/v1/experiments/1"))
                .andExpect(status().isNoContent());

        verify(experimentService).deleteExperiment(1L);
    }

    @Test
    @DisplayName("查询实验版本列表 - 成功")
    void getExperimentBuckets_Success() throws Exception {
        List<Bucket> buckets = List.of(testBucket);
        when(experimentService.getExperimentBuckets(1L)).thenReturn(buckets);

        mockMvc.perform(get("/api/v1/experiments/1/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bucketId").value("control"));

        verify(experimentService).getExperimentBuckets(1L);
    }

    @Test
    @DisplayName("查询实验版本列表 - 空列表")
    void getExperimentBuckets_EmptyList() throws Exception {
        when(experimentService.getExperimentBuckets(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/experiments/1/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(experimentService).getExperimentBuckets(1L);
    }
}