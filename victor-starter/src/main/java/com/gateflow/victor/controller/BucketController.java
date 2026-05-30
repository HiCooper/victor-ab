package com.gateflow.victor.controller;

import com.gateflow.victor.common.util.BucketIdGenerator;
import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.BucketCreateRequest;
import com.gateflow.victor.domain.dto.BucketUpdateRequest;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.service.bucket.BucketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 版本管理 API
 */
@RestController
@RequestMapping("/api/v1/buckets")
@RequiredArgsConstructor
@Tag(name = "Bucket API", description = "版本管理接口")
public class BucketController {

    private final BucketService bucketService;

    @PostMapping
    @Operation(summary = "创建版本", description = "创建单个实验版本")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Bucket> createBucket(@Valid @RequestBody BucketCreateRequest request) {
        Bucket bucket = new Bucket();
        bucket.setExpId(request.getExpId());
        bucket.setBucketId(BucketIdGenerator.generate());
        bucket.setName(request.getName());
        bucket.setBucketStart(request.getBucketStart());
        bucket.setBucketEnd(request.getBucketEnd());
        bucket.setParams(request.getParams());

        Bucket created = bucketService.createBucket(bucket);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/batch")
    @Operation(summary = "批量创建版本", description = "批量创建实验版本")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<List<Bucket>> createBuckets(@Valid @RequestBody List<BucketCreateRequest> requests) {
        List<Bucket> buckets = requests.stream().map(req -> {
            Bucket v = new Bucket();
            v.setExpId(req.getExpId());
            v.setBucketId(BucketIdGenerator.generate());
            v.setName(req.getName());
            v.setBucketStart(req.getBucketStart());
            v.setBucketEnd(req.getBucketEnd());
            v.setParams(req.getParams());
            return v;
        }).collect(Collectors.toList());

        List<Bucket> created = bucketService.createBuckets(buckets);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询版本详情", description = "根据ID查询版本信息")
    public ResponseEntity<Bucket> getBucket(
            @Parameter(description = "版本ID") @PathVariable Long id) {
        Bucket bucket = bucketService.getBucket(id);
        if (bucket == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bucket);
    }

    @GetMapping("/experiment/{expId}")
    @Operation(summary = "查询实验版本列表", description = "查询指定实验的所有版本")
    public ResponseEntity<List<Bucket>> getBucketsByExperiment(
            @Parameter(description = "实验ID") @PathVariable Long expId) {
        List<Bucket> buckets = bucketService.getBucketsByExperimentId(expId);
        return ResponseEntity.ok(buckets);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新版本", description = "更新版本信息")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Bucket> updateBucket(
            @Parameter(description = "版本ID") @PathVariable Long id,
            @RequestBody BucketUpdateRequest request) {
        Bucket bucket = new Bucket();
        bucket.setId(id);
        bucket.setName(request.getName());
        bucket.setBucketStart(request.getBucketStart());
        bucket.setBucketEnd(request.getBucketEnd());
        bucket.setParams(request.getParams());

        Bucket updated = bucketService.updateBucket(bucket);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除版本", description = "删除草稿实验中的版本")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Void> deleteBucket(
            @Parameter(description = "版本ID") @PathVariable Long id) {
        bucketService.deleteBucket(id);
        return ResponseEntity.noContent().build();
    }
}