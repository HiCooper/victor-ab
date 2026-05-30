package com.gateflow.victor.controller;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.*;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.statistics.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 实验统计分析 API
 */
@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
@Tag(name = "Experiment Statistics API", description = "实验统计分析接口")
public class ExperimentStatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/{id}/metrics")
    @Operation(summary = "获取实验指标结果", description = "获取实验的主要指标、辅助指标和护栏指标结果")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<ExperimentMetricsResponse> getMetricResults(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        ExperimentMetricsResponse response = statisticsService.getMetricResults(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/timeseries")
    @Operation(summary = "获取时序数据", description = "获取实验的时序趋势数据")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<TimeSeriesDataResponse> getTimeSeriesData(
            @Parameter(description = "实验ID") @PathVariable Long id,
            @Parameter(description = "天数") @RequestParam(required = false, defaultValue = "14") Integer days) {
        TimeSeriesDataResponse response = statisticsService.getTimeSeriesData(id, days);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/buckets/stats")
    @Operation(summary = "获取分桶统计数据", description = "获取实验各分桶的统计数据和SRM检验结果")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<BucketStatisticsResponse> getBucketStatistics(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        BucketStatisticsResponse response = statisticsService.getBucketStatistics(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/aa-test")
    @Operation(summary = "获取AA测试结果", description = "获取实验的AA回溯验证结果")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<AATestResponse> getAATestResults(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        AATestResponse response = statisticsService.getAATestResults(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/traffic")
    @Operation(summary = "获取流量数据", description = "获取实验的流量分配数据")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<TrafficDataResponse> getTrafficData(
            @Parameter(description = "实验ID") @PathVariable Long id,
            @Parameter(description = "天数") @RequestParam(required = false, defaultValue = "7") Integer days) {
        TrafficDataResponse response = statisticsService.getTrafficData(id, days);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/confidence-trend")
    @Operation(summary = "获取置信度趋势", description = "获取实验的置信度趋势数据")
    @RequirePermission(Permission.VIEW_ANALYSIS)
    public ResponseEntity<ConfidenceTrendResponse> getConfidenceTrend(
            @Parameter(description = "实验ID") @PathVariable Long id,
            @Parameter(description = "天数") @RequestParam(required = false, defaultValue = "30") Integer days) {
        ConfidenceTrendResponse response = statisticsService.getConfidenceTrend(id, days);
        return ResponseEntity.ok(response);
    }
}
