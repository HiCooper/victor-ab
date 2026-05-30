package com.gateflow.victor.controller;

import com.gateflow.victor.common.util.BucketIdGenerator;
import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.dto.ExperimentCreateRequest;
import com.gateflow.victor.domain.dto.ExperimentUpdateRequest;
import com.gateflow.victor.domain.entity.Experiment;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.domain.entity.Bucket;
import com.gateflow.victor.service.experiment.ExperimentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 实验管理 API
 */
@RestController
@RequestMapping("/api/v1/experiments")
@RequiredArgsConstructor
@Tag(name = "Experiment API", description = "实验管理接口")
public class ExperimentController {

    private final ExperimentService experimentService;
    private final com.gateflow.victor.service.experiment.ExperimentLifecycleService lifecycleService;

    @PostMapping
    @Operation(summary = "创建实验", description = "创建新的AB实验")
    @RequirePermission(Permission.CREATE_EXPERIMENT)
    public ResponseEntity<Experiment> createExperiment(
            @Valid @RequestBody ExperimentCreateRequest request) {

        Experiment experiment = new Experiment();
        experiment.setName(request.getName());
        experiment.setDescription(request.getDescription());
        experiment.setLayerId(request.getLayerId());
        experiment.setTargetingRules(request.getTargetingRules());
        experiment.setPrimaryMetric(request.getPrimaryMetric());
        experiment.setSecondaryMetrics(request.getSecondaryMetrics());
        experiment.setGuardrailMetrics(request.getGuardrailMetrics());
        experiment.setCreatedBy(request.getCreatedBy());
        experiment.setAutoRampEnabled(request.getAutoRampEnabled());

        List<Bucket> variants = request.getVariants() != null
            ? request.getVariants().stream().map(vr -> {
                Bucket v = new Bucket();
                v.setBucketId(vr.getVariantKey());
                v.setName(vr.getName());
                v.setBucketStart(vr.getBucketStart());
                v.setBucketEnd(vr.getBucketEnd());
                v.setParams(vr.getParams() != null ? vr.getParams() : "{}");
                return v;
            }).toList()
            : null;

        Experiment created = experimentService.createExperiment(experiment, variants);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询实验详情", description = "根据ID查询实验信息")
    @RequirePermission(Permission.VIEW_EXPERIMENT)
    public ResponseEntity<Experiment> getExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        Experiment experiment = experimentService.getExperiment(id);
        if (experiment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(experiment);
    }

    @GetMapping("/key/{expKey}")
    @Operation(summary = "根据业务标识查询实验", description = "根据expId查询实验信息")
    @RequirePermission(Permission.VIEW_EXPERIMENT)
    public ResponseEntity<Experiment> getExperimentByKey(
            @Parameter(description = "实验业务标识") @PathVariable String expKey) {
        Experiment experiment = experimentService.getExperimentByKey(expKey);
        if (experiment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(experiment);
    }

    @GetMapping
    @Operation(summary = "查询实验列表", description = "查询所有实验或按条件筛选")
    @RequirePermission(Permission.VIEW_EXPERIMENT)
    public ResponseEntity<List<Experiment>> listExperiments(
            @Parameter(description = "层ID") @RequestParam(required = false) Long layerId,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        List<Experiment> experiments = experimentService.listExperiments(layerId, status);
        return ResponseEntity.ok(experiments);
    }

    @GetMapping("/pending")
    @Operation(summary = "查询待审批实验列表", description = "查询所有待审批的实验")
    @RequirePermission(Permission.VIEW_EXPERIMENT)
    public ResponseEntity<List<Experiment>> listPendingExperiments() {
        List<Experiment> experiments = experimentService.listPendingExperiments();
        return ResponseEntity.ok(experiments);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询实验列表", description = "分页查询实验，支持按层ID和状态筛选")
    @RequirePermission(Permission.VIEW_EXPERIMENT)
    public ResponseEntity<Page<Experiment>> listExperimentsPaged(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int current,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "层ID") @RequestParam(required = false) Long layerId,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        Page<Experiment> page = experimentService.listExperimentsPaged(current, size, layerId, status);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新实验", description = "更新实验信息，如果包含variants则创建新版本")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Experiment> updateExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id,
            @Valid @RequestBody ExperimentUpdateRequest request) {

        Experiment experiment = new Experiment();
        experiment.setId(id);
        experiment.setName(request.getName());
        experiment.setDescription(request.getDescription());
        experiment.setTargetingRules(request.getTargetingRules());
        experiment.setPrimaryMetric(request.getPrimaryMetric());
        experiment.setSecondaryMetrics(request.getSecondaryMetrics());
        experiment.setGuardrailMetrics(request.getGuardrailMetrics());

        // 如果包含variants，则创建新版本
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            Experiment updated = experimentService.updateExperimentWithVariants(experiment, request.getVariants());
            return ResponseEntity.ok(updated);
        }
        
        Experiment updated = experimentService.updateExperiment(experiment);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "启动实验", description = "将实验状态改为运行中")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Experiment> startExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        Experiment experiment = experimentService.startExperiment(id);
        return ResponseEntity.ok(experiment);
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "停止实验", description = "停止正在运行的实验")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Experiment> stopExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        Experiment experiment = experimentService.stopExperiment(id);
        return ResponseEntity.ok(experiment);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "提交审批", description = "将实验从草稿提交为待审批状态")
    @RequirePermission(Permission.SUBMIT_APPROVAL)
    public ResponseEntity<Experiment> submitForApproval(
            @Parameter(description = "实验ID") @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "system") String operator) {
        Experiment experiment = experimentService.submitForApproval(id, operator);
        return ResponseEntity.ok(experiment);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "审批通过", description = "审批通过实验，进入运行中状态")
    @RequirePermission(Permission.APPROVE_EXPERIMENT)
    public ResponseEntity<Experiment> approveExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id,
            @RequestParam String operator,
            @RequestParam(required = false) String comment) {
        Experiment experiment = experimentService.approveExperiment(id, operator, comment);
        return ResponseEntity.ok(experiment);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "驳回实验", description = "驳回实验到草稿状态")
    @RequirePermission(Permission.APPROVE_EXPERIMENT)
    public ResponseEntity<Experiment> rejectExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id,
            @RequestParam String operator,
            @RequestParam String reason) {
        Experiment experiment = experimentService.rejectExperiment(id, operator, reason);
        return ResponseEntity.ok(experiment);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档实验", description = "实验结束并归档")
    @RequirePermission(Permission.APPROVE_EXPERIMENT)
    public ResponseEntity<Experiment> archiveExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "rollout_winner") String decision,
            @RequestParam(required = false, defaultValue = "system") String operator) {
        Experiment experiment = experimentService.archiveExperiment(id, decision, operator);
        return ResponseEntity.ok(experiment);
    }

    @PostMapping("/{id}/clone")
    @Operation(summary = "克隆实验", description = "基于现有实验创建新实验草稿")
    @RequirePermission(Permission.CREATE_EXPERIMENT)
    public ResponseEntity<Experiment> cloneExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id,
            @RequestParam String newExpId,
            @RequestParam(required = false, defaultValue = "system") String operator) {
        Experiment experiment = experimentService.cloneExperiment(id, newExpId, operator);
        return ResponseEntity.ok(experiment);
    }

    @GetMapping("/{id}/actions")
    @Operation(summary = "获取可用操作", description = "获取当前实验状态可执行的操作列表")
    @RequirePermission(Permission.VIEW_EXPERIMENT)
    public ResponseEntity<List<String>> getAvailableActions(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        Experiment experiment = experimentService.getExperiment(id);
        if (experiment == null) {
            return ResponseEntity.notFound().build();
        }
        com.gateflow.victor.common.enums.ExperimentStatus status = 
            com.gateflow.victor.common.enums.ExperimentStatus.fromCode(experiment.getStatus());
        return ResponseEntity.ok(lifecycleService.getAvailableActions(status));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除实验", description = "删除草稿或已停止的实验")
    @RequirePermission(Permission.DELETE_EXPERIMENT)
    public ResponseEntity<Void> deleteExperiment(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        experimentService.deleteExperiment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/variants")
    @Operation(summary = "查询实验版本", description = "查询实验的所有版本")
    @RequirePermission(Permission.VIEW_EXPERIMENT)
    public ResponseEntity<List<Bucket>> getExperimentVariants(
            @Parameter(description = "实验ID") @PathVariable Long id) {
        List<Bucket> variants = experimentService.getExperimentVariants(id);
        return ResponseEntity.ok(variants);
    }
}