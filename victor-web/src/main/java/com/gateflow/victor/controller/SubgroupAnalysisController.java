package com.gateflow.victor.controller;

import com.gateflow.victor.domain.dto.SubgroupAnalysisResponse;
import com.gateflow.victor.service.analysis.SubgroupAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 人群拆分分析 API
 */
@RestController
@RequestMapping("/api/v1/subgroup-analysis")
@RequiredArgsConstructor
@Tag(name = "Subgroup Analysis API", description = "人群拆分分析接口")
public class SubgroupAnalysisController {

    private final SubgroupAnalysisService subgroupAnalysisService;

    @GetMapping("/experiments/{expId}/by-platform")
    @Operation(summary = "按平台拆分分析", description = "分析实验效果在不同平台（iOS/Android/Web）的差异")
    public ResponseEntity<SubgroupAnalysisResponse> analyzeByPlatform(
            @Parameter(description = "实验ID") @PathVariable Long expId,
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(subgroupAnalysisService.analyzeByPlatform(expId, startDate, endDate));
    }

    @GetMapping("/experiments/{expId}/by-dimension")
    @Operation(summary = "按自定义维度拆分分析", description = "分析实验效果在任意用户属性维度的差异")
    public ResponseEntity<SubgroupAnalysisResponse> analyzeByDimension(
            @Parameter(description = "实验ID") @PathVariable Long expId,
            @Parameter(description = "拆分维度") @RequestParam String dimension,
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(subgroupAnalysisService.analyzeByDimension(expId, dimension, startDate, endDate));
    }
}
