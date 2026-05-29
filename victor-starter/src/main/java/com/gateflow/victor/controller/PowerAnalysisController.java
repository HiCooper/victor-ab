package com.gateflow.victor.controller;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.PowerAnalysisRequest;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.analysis.PowerAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 功效分析 API
 */
@RestController
@RequestMapping("/api/v1/power-analysis")
@RequiredArgsConstructor
@Tag(name = "Power Analysis API", description = "实验功效计算与样本量预估接口")
public class PowerAnalysisController {

    private final PowerAnalysisService powerAnalysisService;

    @PostMapping("/calculate")
    @Operation(summary = "计算样本量", description = "根据基准转化率、MDE、alpha、power 计算每组所需样本量")
    @RequirePermission(Permission.POWER_ANALYSIS)
    public ResponseEntity<Map<String, Object>> calculateSampleSize(
            @Valid @RequestBody PowerAnalysisRequest request) {
        return ResponseEntity.ok(powerAnalysisService.runPowerAnalysis(request));
    }

    @GetMapping("/estimate-duration")
    @Operation(summary = "预估实验天数", description = "根据样本量和日均UV预估实验运行天数")
    public ResponseEntity<Map<String, Object>> estimateDuration(
            @RequestParam long sampleSizePerGroup,
            @RequestParam(defaultValue = "50") int controlTrafficPercent,
            @RequestParam long dailyUsers) {
        long totalSampleSize = powerAnalysisService.calculateTotalSampleSize(
            0.1, 0.05, 0.05, 0.8, controlTrafficPercent);
        int days = powerAnalysisService.estimateExperimentDuration(
            sampleSizePerGroup, controlTrafficPercent, dailyUsers);

        return ResponseEntity.ok(Map.of(
            "sampleSizePerGroup", sampleSizePerGroup,
            "dailyUsers", dailyUsers,
            "estimatedDays", days,
            "minimumDays", Math.max(days, 7)
        ));
    }
}
