package com.gateflow.victor.service.layer;

import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Domain;
import com.gateflow.victor.infra.mapper.LayerMapper;
import com.gateflow.victor.infra.mapper.DomainMapper;
import com.gateflow.victor.infra.mapper.ExperimentMapper;
import com.gateflow.victor.common.constant.ErrorCode;
import com.gateflow.victor.common.exception.VictorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 层管理服务
 */
@Service
@RequiredArgsConstructor
public class LayerService {

    private final LayerMapper layerMapper;
    private final DomainMapper domainMapper;
    private final ExperimentMapper experimentMapper;

    /**
     * 创建层
     *
     * @param layer 层信息
     * @return 创建的层
     */
    @Transactional(rollbackFor = Exception.class)
    public Layer createLayer(Layer layer) {
        // 验证域是否存在
        if (layer.getDomainId() != null) {
            Domain domain = domainMapper.selectById(layer.getDomainId());
            if (domain == null) {
                throw new VictorException(ErrorCode.DOMAIN_NOT_FOUND, String.valueOf(layer.getDomainId()));
            }
        }

        // 生成盐值（如果未提供）
        if (layer.getSalt() == null || layer.getSalt().isEmpty()) {
            layer.setSalt(UUID.randomUUID().toString().substring(0, 8));
        }

        // 设置盐值和时间戳
        layer.setCreatedAt(LocalDateTime.now());
        layer.setUpdatedAt(LocalDateTime.now());

        layerMapper.insert(layer);

        return layer;
    }

    /**
     * 更新层
     *
     * @param layer 层信息
     * @return 更新后的层
     */
    @Transactional(rollbackFor = Exception.class)
    public Layer updateLayer(Layer layer) {
        Layer existing = layerMapper.selectById(layer.getId());
        if (existing == null) {
            throw new VictorException(ErrorCode.LAYER_NOT_FOUND, String.valueOf(layer.getId()));
        }

        // 盐值不能修改（会影响分桶结果）
        layer.setSalt(existing.getSalt());

        layer.setUpdatedAt(LocalDateTime.now());
        layerMapper.updateById(layer);

        return layer;
    }

    /**
     * 删除层
     *
     * @param layerId 层ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteLayer(Long layerId) {
        Layer layer = layerMapper.selectById(layerId);
        if (layer == null) {
            throw new VictorException(ErrorCode.LAYER_NOT_FOUND, String.valueOf(layerId));
        }

        // 检查是否有实验正在使用
        List<?> experiments = experimentMapper.selectByLayerId(layerId);
        if (!experiments.isEmpty()) {
            throw new VictorException(ErrorCode.LAYER_HAS_EXPERIMENTS);
        }

        layerMapper.deleteById(layerId);
    }

    /**
     * 查询层详情
     *
     * @param layerId 层ID
     * @return 层信息
     */
    public Layer getLayer(Long layerId) {
        return layerMapper.selectById(layerId);
    }

    /**
     * 根据业务ID查询层
     *
     * @param layerKey 层业务标识
     * @return 层信息
     */
    public Layer getLayerByKey(String layerKey) {
        return layerMapper.selectByLayerId(layerKey);
    }

    /**
     * 查询域下的所有层
     *
     * @param domainId 域ID
     * @return 层列表
     */
    public List<Layer> getLayersByDomain(Long domainId) {
        return layerMapper.selectByDomainId(domainId);
    }

    /**
     * 查询所有层
     *
     * @return 层列表
     */
    public List<Layer> listAllLayers() {
        return layerMapper.selectList(null);
    }

    /**
     * 禁用层
     *
     * @param layerId 层ID
     * @return 更新后的层
     */
    @Transactional(rollbackFor = Exception.class)
    public Layer disableLayer(Long layerId) {
        Layer layer = layerMapper.selectById(layerId);
        if (layer == null) {
            throw new VictorException(ErrorCode.LAYER_NOT_FOUND, String.valueOf(layerId));
        }

        layer.setEnabled(false);
        layerMapper.updateById(layer);

        return layer;
    }

    @Transactional(rollbackFor = Exception.class)
    public Layer enableLayer(Long layerId) {
        Layer layer = layerMapper.selectById(layerId);
        if (layer == null) {
            throw new VictorException(ErrorCode.LAYER_NOT_FOUND, String.valueOf(layerId));
        }

        layer.setEnabled(true);
        layerMapper.updateById(layer);

        return layer;
    }
}