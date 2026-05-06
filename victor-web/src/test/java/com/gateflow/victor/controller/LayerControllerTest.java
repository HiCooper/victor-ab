package com.gateflow.victor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateflow.victor.domain.dto.LayerCreateRequest;
import com.gateflow.victor.domain.dto.LayerUpdateRequest;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.service.layer.LayerService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LayerController 集成测试
 */
@WebMvcTest(LayerController.class)
class LayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LayerService layerService;

    private Layer testLayer;

    @BeforeEach
    void setUp() {
        testLayer = new Layer();
        testLayer.setId(1L);
        testLayer.setLayerId("layer_ui");
        testLayer.setDomainId(1L);
        testLayer.setName("UI层");
        testLayer.setSalt("ui_salt_001");
        testLayer.setSortOrder(1);
    }

    @Test
    @DisplayName("创建层 - 成功")
    void createLayer_Success() throws Exception {
        LayerCreateRequest request = new LayerCreateRequest();
        request.setLayerId("layer_ui");
        request.setDomainId(1L);
        request.setName("UI层");

        when(layerService.createLayer(any(Layer.class))).thenReturn(testLayer);

        mockMvc.perform(post("/api/v1/layers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layerId").value("layer_ui"))
                .andExpect(jsonPath("$.name").value("UI层"));

        verify(layerService).createLayer(any(Layer.class));
    }

    @Test
    @DisplayName("创建层 - 缺少必填字段")
    void createLayer_MissingRequiredField() throws Exception {
        LayerCreateRequest request = new LayerCreateRequest();
        request.setName("UI层"); // 缺少layerId

        mockMvc.perform(post("/api/v1/layers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(layerService, never()).createLayer(any());
    }

    @Test
    @DisplayName("查询层详情 - 成功")
    void getLayer_Success() throws Exception {
        when(layerService.getLayer(1L)).thenReturn(testLayer);

        mockMvc.perform(get("/api/v1/layers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.layerId").value("layer_ui"));

        verify(layerService).getLayer(1L);
    }

    @Test
    @DisplayName("查询层详情 - 不存在")
    void getLayer_NotFound() throws Exception {
        when(layerService.getLayer(999L)).thenReturn(null);

        mockMvc.perform(get("/api/v1/layers/999"))
                .andExpect(status().isNotFound());

        verify(layerService).getLayer(999L);
    }

    @Test
    @DisplayName("根据业务标识查询层 - 成功")
    void getLayerByKey_Success() throws Exception {
        when(layerService.getLayerByKey("layer_ui")).thenReturn(testLayer);

        mockMvc.perform(get("/api/v1/layers/key/layer_ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layerId").value("layer_ui"));

        verify(layerService).getLayerByKey("layer_ui");
    }

    @Test
    @DisplayName("根据业务标识查询层 - 不存在")
    void getLayerByKey_NotFound() throws Exception {
        when(layerService.getLayerByKey("not_exist")).thenReturn(null);

        mockMvc.perform(get("/api/v1/layers/key/not_exist"))
                .andExpect(status().isNotFound());

        verify(layerService).getLayerByKey("not_exist");
    }

    @Test
    @DisplayName("查询所有层 - 成功")
    void listAllLayers_Success() throws Exception {
        List<Layer> layers = List.of(testLayer);
        when(layerService.listAllLayers()).thenReturn(layers);

        mockMvc.perform(get("/api/v1/layers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].layerId").value("layer_ui"));

        verify(layerService).listAllLayers();
    }

    @Test
    @DisplayName("查询所有层 - 空列表")
    void listAllLayers_EmptyList() throws Exception {
        when(layerService.listAllLayers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/layers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(layerService).listAllLayers();
    }

    @Test
    @DisplayName("查询域下的层 - 成功")
    void getLayersByDomain_Success() throws Exception {
        List<Layer> layers = List.of(testLayer);
        when(layerService.getLayersByDomain(1L)).thenReturn(layers);

        mockMvc.perform(get("/api/v1/layers/domain/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].domainId").value(1));

        verify(layerService).getLayersByDomain(1L);
    }

    @Test
    @DisplayName("更新层 - 成功")
    void updateLayer_Success() throws Exception {
        LayerUpdateRequest request = new LayerUpdateRequest();
        request.setName("更新后的名称");
        request.setSortOrder(2);

        Layer updated = new Layer();
        updated.setId(1L);
        updated.setName("更新后的名称");
        updated.setSortOrder(2);

        when(layerService.updateLayer(any(Layer.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/layers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("更新后的名称"));

        verify(layerService).updateLayer(any(Layer.class));
    }

    @Test
    @DisplayName("启用层 - 成功")
    void enableLayer_Success() throws Exception {
        when(layerService.enableLayer(1L)).thenReturn(testLayer);

        mockMvc.perform(post("/api/v1/layers/1/enable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(layerService).enableLayer(1L);
    }

    @Test
    @DisplayName("禁用层 - 成功")
    void disableLayer_Success() throws Exception {
        Layer disabled = new Layer();
        disabled.setId(1L);
        disabled.setName("UI层");

        when(layerService.disableLayer(1L)).thenReturn(disabled);

        mockMvc.perform(post("/api/v1/layers/1/disable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(layerService).disableLayer(1L);
    }

    @Test
    @DisplayName("删除层 - 成功")
    void deleteLayer_Success() throws Exception {
        doNothing().when(layerService).deleteLayer(1L);

        mockMvc.perform(delete("/api/v1/layers/1"))
                .andExpect(status().isNoContent());

        verify(layerService).deleteLayer(1L);
    }
}