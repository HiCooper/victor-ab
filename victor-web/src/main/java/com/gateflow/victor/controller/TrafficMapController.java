package com.gateflow.victor.controller;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.TrafficMapResponse;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.traffic.TrafficMapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 流量地图 API
 */
@RestController
@RequestMapping("/api/v1/traffic")
@RequiredArgsConstructor
@Tag(name = "Traffic Map API", description = "流量可视化地图接口")
public class TrafficMapController {

    private final TrafficMapService trafficMapService;

    @GetMapping("/map")
    @Operation(summary = "获取全局流量地图", description = "返回所有域/层/实验的流量占用情况")
    @RequirePermission(Permission.VIEW_TRAFFIC)
    public ResponseEntity<TrafficMapResponse> getTrafficMap() {
        return ResponseEntity.ok(trafficMapService.getTrafficMap());
    }

    @GetMapping("/layers/{layerId}/occupancy")
    @Operation(summary = "获取单层占用详情", description = "返回指定层的桶占用详情和冲突检测")
    @RequirePermission(Permission.VIEW_TRAFFIC)
    public ResponseEntity<TrafficMapResponse.LayerDetailResponse> getLayerOccupancy(
            @Parameter(description = "层ID") @PathVariable Long layerId) {
        return ResponseEntity.ok(trafficMapService.getLayerOccupancy(layerId));
    }

    @GetMapping("/layers/{layerId}/conflicts")
    @Operation(summary = "检测层内桶冲突", description = "返回指定层内所有实验的桶冲突列表")
    @RequirePermission(Permission.VIEW_TRAFFIC)
    public ResponseEntity<java.util.List<TrafficMapResponse.ConflictWarning>> detectLayerConflicts(
            @Parameter(description = "层ID") @PathVariable Long layerId) {
        return ResponseEntity.ok(trafficMapService.detectLayerConflicts(layerId));
    }
}
