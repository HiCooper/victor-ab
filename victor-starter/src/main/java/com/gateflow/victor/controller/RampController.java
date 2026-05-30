package com.gateflow.victor.controller;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.ramp.RampScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 灰度推进 API
 */
@RestController
@RequestMapping("/api/v1/ramp")
@RequiredArgsConstructor
@Tag(name = "Ramp API", description = "动态灰度推进接口")
public class RampController {

    private final RampScheduler rampScheduler;

    @GetMapping("/experiments/{expId}/status")
    @Operation(summary = "获取灰度状态", description = "返回实验当前灰度阶段和下一阶段信息")
    public ResponseEntity<Map<String, Object>> getRampStatus(
            @Parameter(description = "实验ID") @PathVariable Long expId) {
        return ResponseEntity.ok(rampScheduler.getRampStatus(expId));
    }

    @PostMapping("/experiments/{expId}/advance")
    @Operation(summary = "手动推进灰度", description = "手动将实验推进到下一个灰度阶段")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Map<String, Object>> advanceRamp(
            @Parameter(description = "实验ID") @PathVariable Long expId) {
        rampScheduler.manuallyAdvanceRamp(expId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "灰度已推进"
        ));
    }
}
