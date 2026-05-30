package com.gateflow.victor.controller;

import com.gateflow.victor.common.util.BucketIdGenerator;
import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.VariantCreateRequest;
import com.gateflow.victor.domain.dto.VariantUpdateRequest;
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
@RequestMapping("/api/v1/variants")
@RequiredArgsConstructor
@Tag(name = "Variant API", description = "版本管理接口")
public class BucketController {

    private final BucketService bucketService;

    @PostMapping
    @Operation(summary = "创建版本", description = "创建单个实验版本")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Bucket> createVariant(@Valid @RequestBody VariantCreateRequest request) {
        Bucket variant = new Bucket();
        variant.setExpId(request.getExpId());
        variant.setBucketId(request.getVariantKey());
        variant.setName(request.getName());
        variant.setBucketStart(request.getBucketStart());
        variant.setBucketEnd(request.getBucketEnd());
        variant.setParams(request.getParams());

        Bucket created = bucketService.createVariant(variant);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/batch")
    @Operation(summary = "批量创建版本", description = "批量创建实验版本")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<List<Bucket>> createVariants(@Valid @RequestBody List<VariantCreateRequest> requests) {
        List<Bucket> variants = requests.stream().map(req -> {
            Bucket v = new Bucket();
            v.setExpId(req.getExpId());
            v.setBucketId(req.getVariantKey());
            v.setName(req.getName());
            v.setBucketStart(req.getBucketStart());
            v.setBucketEnd(req.getBucketEnd());
            v.setParams(req.getParams());
            return v;
        }).collect(Collectors.toList());

        List<Bucket> created = bucketService.createVariants(variants);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询版本详情", description = "根据ID查询版本信息")
    public ResponseEntity<Bucket> getVariant(
            @Parameter(description = "版本ID") @PathVariable Long id) {
        Bucket variant = bucketService.getVariant(id);
        if (variant == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(variant);
    }

    @GetMapping("/experiment/{expId}")
    @Operation(summary = "查询实验版本列表", description = "查询指定实验的所有版本")
    public ResponseEntity<List<Bucket>> getVariantsByExperiment(
            @Parameter(description = "实验ID") @PathVariable Long expId) {
        List<Bucket> variants = bucketService.getVariantsByExperimentId(expId);
        return ResponseEntity.ok(variants);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新版本", description = "更新版本信息")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Bucket> updateVariant(
            @Parameter(description = "版本ID") @PathVariable Long id,
            @RequestBody VariantUpdateRequest request) {
        Bucket variant = new Bucket();
        variant.setId(id);
        variant.setName(request.getName());
        variant.setBucketStart(request.getBucketStart());
        variant.setBucketEnd(request.getBucketEnd());
        variant.setParams(request.getParams());

        Bucket updated = bucketService.updateVariant(variant);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除版本", description = "删除草稿实验中的版本")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Void> deleteVariant(
            @Parameter(description = "版本ID") @PathVariable Long id) {
        bucketService.deleteVariant(id);
        return ResponseEntity.noContent().build();
    }
}