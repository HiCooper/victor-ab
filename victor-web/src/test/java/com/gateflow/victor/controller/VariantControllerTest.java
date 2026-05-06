package com.gateflow.victor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.domain.dto.VariantCreateRequest;
import com.gateflow.victor.domain.dto.VariantUpdateRequest;
import com.gateflow.victor.domain.entity.Variant;
import com.gateflow.victor.service.variant.VariantService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VariantController 集成测试
 */
@WebMvcTest(VariantController.class)
class VariantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VariantService variantService;

    private Variant testVariant;

    @BeforeEach
    void setUp() {
        testVariant = new Variant();
        testVariant.setId(1L);
        testVariant.setExpId(1L);
        testVariant.setVariantKey("control");
        testVariant.setName("对照组");
        testVariant.setBucketStart(0);
        testVariant.setBucketEnd(499);
        testVariant.setParams("{\"color\": \"blue\"}");
    }

    @Test
    @DisplayName("创建版本 - 成功")
    void createVariant_Success() throws Exception {
        VariantCreateRequest request = new VariantCreateRequest();
        request.setExpId(1L);
        request.setVariantKey("control");
        request.setName("对照组");
        request.setBucketStart(0);
        request.setBucketEnd(499);

        when(variantService.createVariant(any(Variant.class))).thenReturn(testVariant);

        mockMvc.perform(post("/api/v1/variants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantKey").value("control"))
                .andExpect(jsonPath("$.name").value("对照组"));

        verify(variantService).createVariant(any(Variant.class));
    }

    @Test
    @DisplayName("创建版本 - 缺少必填字段")
    void createVariant_MissingRequiredField() throws Exception {
        VariantCreateRequest request = new VariantCreateRequest();
        request.setExpId(1L);
        request.setName("对照组"); // 缺少variantKey

        mockMvc.perform(post("/api/v1/variants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(variantService, never()).createVariant(any());
    }

    @Test
    @DisplayName("批量创建版本 - 成功")
    void createVariants_Success() throws Exception {
        VariantCreateRequest request1 = new VariantCreateRequest();
        request1.setExpId(1L);
        request1.setVariantKey("control");
        request1.setName("对照组");
        request1.setBucketStart(0);
        request1.setBucketEnd(499);

        VariantCreateRequest request2 = new VariantCreateRequest();
        request2.setExpId(1L);
        request2.setVariantKey("treatment");
        request2.setName("实验组");
        request2.setBucketStart(500);
        request2.setBucketEnd(999);

        List<VariantCreateRequest> requests = List.of(request1, request2);
        List<Variant> variants = List.of(testVariant);

        when(variantService.createVariants(anyList())).thenReturn(variants);

        mockMvc.perform(post("/api/v1/variants/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk());

        verify(variantService).createVariants(anyList());
    }

    @Test
    @DisplayName("查询版本详情 - 成功")
    void getVariant_Success() throws Exception {
        when(variantService.getVariant(1L)).thenReturn(testVariant);

        mockMvc.perform(get("/api/v1/variants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.variantKey").value("control"));

        verify(variantService).getVariant(1L);
    }

    @Test
    @DisplayName("查询版本详情 - 不存在")
    void getVariant_NotFound() throws Exception {
        when(variantService.getVariant(999L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/variants/999"))
                .andExpect(status().isNotFound());

        verify(variantService).getVariant(999L);
    }

    @Test
    @DisplayName("查询实验版本列表 - 成功")
    void getVariantsByExperiment_Success() throws Exception {
        List<Variant> variants = List.of(testVariant);
        when(variantService.getVariantsByExperiment(1L)).thenReturn(variants);

        mockMvc.perform(get("/api/v1/variants/experiment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variantKey").value("control"));

        verify(variantService).getVariantsByExperiment(1L);
    }

    @Test
    @DisplayName("查询实验版本列表 - 空列表")
    void getVariantsByExperiment_EmptyList() throws Exception {
        when(variantService.getVariantsByExperiment(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/variants/experiment/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(variantService).getVariantsByExperiment(1L);
    }

    @Test
    @DisplayName("更新版本 - 成功")
    void updateVariant_Success() throws Exception {
        VariantUpdateRequest request = new VariantUpdateRequest();
        request.setName("更新后的名称");
        request.setBucketStart(0);
        request.setBucketEnd(599);

        Variant updated = new Variant();
        updated.setId(1L);
        updated.setName("更新后的名称");

        when(variantService.updateVariant(any(Variant.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/variants/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("更新后的名称"));

        verify(variantService).updateVariant(any(Variant.class));
    }

    @Test
    @DisplayName("删除版本 - 成功")
    void deleteVariant_Success() throws Exception {
        doNothing().when(variantService).deleteVariant(1L);

        mockMvc.perform(delete("/api/v1/variants/1"))
                .andExpect(status().isNoContent());

        verify(variantService).deleteVariant(1L);
    }
}