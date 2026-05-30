package com.gateflow.victor.controller;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.experiment.BucketVersionService;
import com.gateflow.victor.service.experiment.ExperimentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 实验版本管理 API
 */
@RestController
@RequestMapping("/api/v1/experiments/{expId}/versions")
@RequiredArgsConstructor
@Tag(name = "Experiment Version API", description = "实验版本管理接口")
public class ExperimentVersionController {

    private final ExperimentService experimentService;
    private final BucketVersionService versionService;

    @GetMapping
    @Operation(summary = "获取版本历史", description = "获取实验的所有版本号列表")
    public ResponseEntity<List<String>> getVersionHistory(
            @Parameter(description = "实验ID") @PathVariable Long expId) {
        List<String> versions = experimentService.getVersionHistory(expId);
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/active")
    @Operation(summary = "获取当前活跃版本", description = "获取实验的当前活跃版本配置")
    public ResponseEntity<List<Bucket>> getActiveBuckets(
            @Parameter(description = "实验ID") @PathVariable Long expId) {
        List<Bucket> buckets = experimentService.getExperimentBuckets(expId);
        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有历史版本", description = "获取实验的所有历史版本（包含非活跃）")
    public ResponseEntity<List<Bucket>> getAllBuckets(
            @Parameter(description = "实验ID") @PathVariable Long expId) {
        List<Bucket> buckets = experimentService.getAllExperimentBuckets(expId);
        return ResponseEntity.ok(buckets);
    }

    @GetMapping("/{version}")
    @Operation(summary = "获取指定版本", description = "获取实验的指定版本配置")
    public ResponseEntity<List<Bucket>> getBucketByVersion(
            @Parameter(description = "实验ID") @PathVariable Long expId,
            @Parameter(description = "版本号") @PathVariable String version) {
        List<Bucket> buckets = experimentService.getExperimentBucketsByVersion(expId, version);
        return ResponseEntity.ok(buckets);
    }

    @PostMapping("/rollback/{version}")
    @Operation(summary = "回滚到指定版本", description = "将实验回滚到指定的历史版本")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Map<String, Object>> rollbackToVersion(
            @Parameter(description = "实验ID") @PathVariable Long expId,
            @Parameter(description = "目标版本号") @PathVariable String version) {
        List<Bucket> buckets = versionService.rollbackToVersion(expId, version);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Successfully rolled back to version " + version,
                "version", version,
                "bucketCount", buckets.size()
        ));
    }

    @GetMapping("/compare")
    @Operation(summary = "对比两个版本", description = "对比实验的两个版本之间的差异")
    public ResponseEntity<BucketVersionService.VersionComparison> compareVersions(
            @Parameter(description = "实验ID") @PathVariable Long expId,
            @Parameter(description = "版本1") @RequestParam String v1,
            @Parameter(description = "版本2") @RequestParam String v2) {
        BucketVersionService.VersionComparison comparison = versionService.compareVersions(expId, v1, v2);
        return ResponseEntity.ok(comparison);
    }

    @DeleteMapping("/cleanup")
    @Operation(summary = "清理旧版本", description = "清理实验的旧版本，保留最近N个版本")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Map<String, Object>> cleanupOldVersions(
            @Parameter(description = "实验ID") @PathVariable Long expId,
            @Parameter(description = "保留版本数量") @RequestParam(defaultValue = "10") int keep) {
        int deleted = versionService.cleanupOldVersions(expId, keep);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Cleaned up " + deleted + " old versions",
                "deletedCount", deleted,
                "keptCount", keep
        ));
    }
}
