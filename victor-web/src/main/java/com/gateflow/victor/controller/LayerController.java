package com.gateflow.victor.controller;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.LayerCreateRequest;
import com.gateflow.victor.domain.dto.LayerUpdateRequest;
import com.gateflow.victor.domain.entity.Layer;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.layer.LayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 层管理 API
 */
@RestController
@RequestMapping("/api/v1/layers")
@RequiredArgsConstructor
@Tag(name = "Layer API", description = "层管理接口")
public class LayerController {

    private final LayerService layerService;

    @PostMapping
    @Operation(summary = "创建层", description = "创建新的分流层")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Layer> createLayer(@Valid @RequestBody LayerCreateRequest request) {
        Layer layer = new Layer();
        layer.setLayerId(request.getLayerId());
        layer.setDomainId(request.getDomainId());
        layer.setName(request.getName());
        layer.setSalt(request.getSalt());
        layer.setSortOrder(request.getSortOrder());

        Layer created = layerService.createLayer(layer);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询层详情", description = "根据ID查询层信息")
    public ResponseEntity<Layer> getLayer(
            @Parameter(description = "层ID") @PathVariable Long id) {
        Layer layer = layerService.getLayer(id);
        if (layer == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(layer);
    }

    @GetMapping("/key/{layerKey}")
    @Operation(summary = "根据业务标识查询层", description = "根据layerId查询层信息")
    public ResponseEntity<Layer> getLayerByKey(
            @Parameter(description = "层业务标识") @PathVariable String layerKey) {
        Layer layer = layerService.getLayerByKey(layerKey);
        if (layer == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(layer);
    }

    @GetMapping
    @Operation(summary = "查询所有层", description = "查询所有分流层列表")
    public ResponseEntity<List<Layer>> listAllLayers() {
        List<Layer> layers = layerService.listAllLayers();
        return ResponseEntity.ok(layers);
    }

    @GetMapping("/domain/{domainId}")
    @Operation(summary = "查询域下的层", description = "查询指定域下的所有层")
    public ResponseEntity<List<Layer>> getLayersByDomain(
            @Parameter(description = "域ID") @PathVariable Long domainId) {
        List<Layer> layers = layerService.getLayersByDomain(domainId);
        return ResponseEntity.ok(layers);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新层", description = "更新层信息")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Layer> updateLayer(
            @Parameter(description = "层ID") @PathVariable Long id,
            @RequestBody LayerUpdateRequest request) {
        Layer layer = new Layer();
        layer.setId(id);
        layer.setName(request.getName());
        layer.setSortOrder(request.getSortOrder());

        Layer updated = layerService.updateLayer(layer);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用层", description = "启用禁用的层")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Layer> enableLayer(
            @Parameter(description = "层ID") @PathVariable Long id) {
        Layer layer = layerService.enableLayer(id);
        return ResponseEntity.ok(layer);
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用层", description = "禁用层")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Layer> disableLayer(
            @Parameter(description = "层ID") @PathVariable Long id) {
        Layer layer = layerService.disableLayer(id);
        return ResponseEntity.ok(layer);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除层", description = "删除没有实验的层")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Void> deleteLayer(
            @Parameter(description = "层ID") @PathVariable Long id) {
        layerService.deleteLayer(id);
        return ResponseEntity.noContent().build();
    }
}