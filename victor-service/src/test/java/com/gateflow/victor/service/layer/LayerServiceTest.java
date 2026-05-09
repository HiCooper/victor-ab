package com.gateflow.victor.service.layer;

import com.gateflow.victor.common.exception.VictorException;
import com.gateflow.victor.domain.entity.Domain;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.infra.mapper.DomainMapper;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.infra.mapper.LayerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * LayerService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LayerServiceTest {

    @Mock
    private LayerMapper layerMapper;

    @Mock
    private DomainMapper domainMapper;

    @Mock
    private ExperimentMapper experimentMapper;

    @InjectMocks
    private LayerService layerService;

    private Layer testLayer;
    private Domain testDomain;

    @BeforeEach
    void setUp() {
        testDomain = new Domain();
        testDomain.setId(1L);
        testDomain.setName("默认域");

        testLayer = new Layer();
        testLayer.setId(1L);
        testLayer.setLayerId("layer_ui");
        testLayer.setDomainId(1L);
        testLayer.setName("UI层");
        testLayer.setSalt("ui_salt");
        testLayer.setSortOrder(1);
    }

    @Test
    @DisplayName("创建层 - 成功")
    void createLayer_Success() {
        when(domainMapper.selectById(1L)).thenReturn(testDomain);
        when(layerMapper.insert(any(Layer.class))).thenReturn(1);

        Layer layer = new Layer();
        layer.setLayerId("layer_new");
        layer.setDomainId(1L);
        layer.setName("新层");

        Layer created = layerService.createLayer(layer);

        assertNotNull(created);
        assertNotNull(created.getSalt()); // 自动生成盐值
        verify(layerMapper).insert(any(Layer.class));
    }

    @Test
    @DisplayName("创建层 - 指定盐值")
    void createLayer_WithSalt() {
        when(domainMapper.selectById(1L)).thenReturn(testDomain);
        when(layerMapper.insert(any(Layer.class))).thenReturn(1);

        Layer layer = new Layer();
        layer.setLayerId("layer_new");
        layer.setDomainId(1L);
        layer.setName("新层");
        layer.setSalt("custom_salt");

        Layer created = layerService.createLayer(layer);

        assertNotNull(created);
        assertEquals("custom_salt", created.getSalt()); // 使用指定的盐值
        verify(layerMapper).insert(any(Layer.class));
    }

    @Test
    @DisplayName("创建层 - 域不存在")
    void createLayer_DomainNotFound() {
        when(domainMapper.selectById(999L)).thenReturn(null);

        Layer layer = new Layer();
        layer.setLayerId("layer_new");
        layer.setDomainId(999L);
        layer.setName("新层");

        VictorException exception = assertThrows(VictorException.class,
                () -> layerService.createLayer(layer));

        assertTrue(exception.getMessage().contains("分域不存在"));
        verify(layerMapper, never()).insert(any(Layer.class));
    }

    @Test
    @DisplayName("创建层 - 无域ID")
    void createLayer_NoDomainId() {
        when(layerMapper.insert(any(Layer.class))).thenReturn(1);

        Layer layer = new Layer();
        layer.setLayerId("layer_new");
        layer.setName("新层");
        // 不设置domainId

        Layer created = layerService.createLayer(layer);

        assertNotNull(created);
        verify(domainMapper, never()).selectById(any());
        verify(layerMapper).insert(any(Layer.class));
    }

    @Test
    @DisplayName("查询层 - 成功")
    void getLayer_Success() {
        when(layerMapper.selectById(1L)).thenReturn(testLayer);

        Layer found = layerService.getLayer(1L);

        assertNotNull(found);
        assertEquals("layer_ui", found.getLayerId());
        verify(layerMapper).selectById(1L);
    }

    @Test
    @DisplayName("查询层 - 不存在")
    void getLayer_NotFound() {
        when(layerMapper.selectById(999L)).thenReturn(null);

        Layer found = layerService.getLayer(999L);

        assertNull(found);
        verify(layerMapper).selectById(999L);
    }

    @Test
    @DisplayName("根据业务标识查询层 - 成功")
    void getLayerByKey_Success() {
        when(layerMapper.selectByLayerId("layer_ui")).thenReturn(testLayer);

        Layer found = layerService.getLayerByKey("layer_ui");

        assertNotNull(found);
        assertEquals("layer_ui", found.getLayerId());
        verify(layerMapper).selectByLayerId("layer_ui");
    }

    @Test
    @DisplayName("查询所有层 - 成功")
    void listAllLayers_Success() {
        List<Layer> layers = List.of(testLayer);
        when(layerMapper.selectList(any())).thenReturn(layers);

        List<Layer> result = layerService.listAllLayers();

        assertEquals(1, result.size());
        verify(layerMapper).selectList(any());
    }

    @Test
    @DisplayName("查询域下的层 - 成功")
    void getLayersByDomain_Success() {
        List<Layer> layers = List.of(testLayer);
        when(layerMapper.selectByDomainId(1L)).thenReturn(layers);

        List<Layer> result = layerService.getLayersByDomain(1L);

        assertEquals(1, result.size());
        verify(layerMapper).selectByDomainId(1L);
    }

    @Test
    @DisplayName("更新层 - 成功")
    void updateLayer_Success() {
        Layer existing = new Layer();
        existing.setId(1L);
        existing.setSalt("original_salt");

        when(layerMapper.selectById(1L)).thenReturn(existing);
        when(layerMapper.updateById(any(Layer.class))).thenReturn(1);

        Layer update = new Layer();
        update.setId(1L);
        update.setName("更新后的名称");
        update.setSalt("new_salt"); // 尝试修改盐值

        Layer result = layerService.updateLayer(update);

        assertNotNull(result);
        assertEquals("original_salt", result.getSalt()); // 盐值不会被修改
        verify(layerMapper).updateById(any(Layer.class));
    }

    @Test
    @DisplayName("更新层 - 层不存在")
    void updateLayer_NotFound() {
        when(layerMapper.selectById(999L)).thenReturn(null);

        Layer update = new Layer();
        update.setId(999L);
        update.setName("更新后的名称");

        VictorException exception = assertThrows(VictorException.class,
                () -> layerService.updateLayer(update));

        assertTrue(exception.getMessage().contains("层不存在"));
        verify(layerMapper, never()).updateById(any(Layer.class));
    }

    @Test
    @DisplayName("启用层 - 成功")
    void enableLayer_Success() {
        when(layerMapper.selectById(1L)).thenReturn(testLayer);
        when(layerMapper.updateById(any(Layer.class))).thenReturn(1);

        Layer result = layerService.enableLayer(1L);

        assertNotNull(result);
        verify(layerMapper).updateById(any(Layer.class));
    }

    @Test
    @DisplayName("禁用层 - 成功")
    void disableLayer_Success() {
        when(layerMapper.selectById(1L)).thenReturn(testLayer);
        when(layerMapper.updateById(any(Layer.class))).thenReturn(1);

        Layer result = layerService.disableLayer(1L);

        assertNotNull(result);
        verify(layerMapper).updateById(any(Layer.class));
    }

    @Test
    @DisplayName("删除层 - 成功")
    void deleteLayer_Success() {
        when(layerMapper.selectById(1L)).thenReturn(testLayer);
        when(experimentMapper.selectByLayerId(1L)).thenReturn(Collections.emptyList());
        when(layerMapper.deleteById(anyLong())).thenReturn(1);

        layerService.deleteLayer(1L);

        verify(layerMapper).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除层 - 层不存在")
    void deleteLayer_NotFound() {
        when(layerMapper.selectById(999L)).thenReturn(null);

        VictorException exception = assertThrows(VictorException.class,
                () -> layerService.deleteLayer(999L));

        assertTrue(exception.getMessage().contains("层不存在"));
        verify(layerMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除层 - 有实验正在使用")
    void deleteLayer_HasExperiments() {
        Experiment experiment = new Experiment();
        experiment.setId(1L);

        when(layerMapper.selectById(1L)).thenReturn(testLayer);
        when(experimentMapper.selectByLayerId(1L)).thenReturn(List.of(experiment));

        VictorException exception = assertThrows(VictorException.class,
                () -> layerService.deleteLayer(1L));

        assertTrue(exception.getMessage().contains("无法删除"));
        verify(layerMapper, never()).deleteById(anyLong());
    }
}