package com.gateflow.victor.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.domain.dto.BucketCreateRequest;
import com.gateflow.victor.domain.dto.BucketUpdateRequest;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.service.bucket.BucketService;
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
 * BucketController 集成测试
 */
@WebMvcTest(BucketController.class)
class BucketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BucketService bucketService;

    private Bucket testBucket;

    @BeforeEach
    void setUp() {
        testBucket = new Bucket();
        testBucket.setId(1L);
        testBucket.setExpId("exp_test_001");
        testBucket.setBucketId("control");
        testBucket.setName("对照组");
        testBucket.setBucketStart(0);
        testBucket.setBucketEnd(499);
        testBucket.setParams("{\"color\": \"blue\"}");
    }

    @Test
    @DisplayName("创建版本 - 成功")
    void createBucket_Success() throws Exception {
        BucketCreateRequest request = new BucketCreateRequest();
        request.setExpId("exp_test_001");
        request.setName("对照组");
        request.setBucketStart(0);
        request.setBucketEnd(499);

        when(bucketService.createBucket(any(Bucket.class))).thenReturn(testBucket);

        mockMvc.perform(post("/api/v1/admin/buckets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucketId").value("control"))
                .andExpect(jsonPath("$.name").value("对照组"));

        verify(bucketService).createBucket(any(Bucket.class));
    }

    @Test
    @DisplayName("创建版本 - 缺少必填字段")
    void createBucket_MissingRequiredField() throws Exception {
        BucketCreateRequest request = new BucketCreateRequest();
        request.setExpId("exp_test_001");
        request.setName("对照组"); 

        mockMvc.perform(post("/api/v1/admin/buckets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(bucketService, never()).createBucket(any());
    }

    @Test
    @DisplayName("批量创建版本 - 成功")
    void createBuckets_Success() throws Exception {
        BucketCreateRequest request1 = new BucketCreateRequest();
        request1.setExpId("exp_test_001");
        request1.setName("对照组");
        request1.setBucketStart(0);
        request1.setBucketEnd(499);

        BucketCreateRequest request2 = new BucketCreateRequest();
        request2.setExpId("exp_test_001");
        request2.setName("实验组");
        request2.setBucketStart(500);
        request2.setBucketEnd(999);

        List<BucketCreateRequest> requests = List.of(request1, request2);
        List<Bucket> buckets = List.of(testBucket);

        when(bucketService.createBuckets(anyList())).thenReturn(buckets);

        mockMvc.perform(post("/api/v1/buckets/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk());

        verify(bucketService).createBuckets(anyList());
    }

    @Test
    @DisplayName("查询版本详情 - 成功")
    void getBucket_Success() throws Exception {
        when(bucketService.getBucket(1L)).thenReturn(testBucket);

        mockMvc.perform(get("/api/v1/buckets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.bucketId").value("control"));

        verify(bucketService).getBucket(1L);
    }

    @Test
    @DisplayName("查询版本详情 - 不存在")
    void getBucket_NotFound() throws Exception {
        when(bucketService.getBucket(999L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/buckets/999"))
                .andExpect(status().isNotFound());

        verify(bucketService).getBucket(999L);
    }

    @Test
    @DisplayName("查询实验版本列表 - 成功")
    void getBucketsByExperiment_Success() throws Exception {
        List<Bucket> buckets = List.of(testBucket);
        when(bucketService.getBucketsByExperimentId(1L)).thenReturn(buckets);

        mockMvc.perform(get("/api/v1/buckets/experiment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bucketId").value("control"));

        verify(bucketService).getBucketsByExperimentId(1L);
    }

    @Test
    @DisplayName("查询实验版本列表 - 空列表")
    void getBucketsByExperiment_EmptyList() throws Exception {
        when(bucketService.getBucketsByExperimentId(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/buckets/experiment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(bucketService).getBucketsByExperimentId(1L);
    }

    @Test
    @DisplayName("更新版本 - 成功")
    void updateBucket_Success() throws Exception {
        BucketUpdateRequest request = new BucketUpdateRequest();
        request.setName("更新后的名称");
        request.setBucketStart(0);
        request.setBucketEnd(599);

        Bucket updated = new Bucket();
        updated.setId(1L);
        updated.setName("更新后的名称");

        when(bucketService.updateBucket(any(Bucket.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/buckets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("更新后的名称"));

        verify(bucketService).updateBucket(any(Bucket.class));
    }

    @Test
    @DisplayName("删除版本 - 成功")
    void deleteBucket_Success() throws Exception {
        doNothing().when(bucketService).deleteBucket(1L);

        mockMvc.perform(delete("/api/v1/buckets/1"))
                .andExpect(status().isNoContent());

        verify(bucketService).deleteBucket(1L);
    }
}
