package com.gateflow.victor.controller.admin;

import com.gateflow.victor.config.RequirePermission;
import com.gateflow.victor.domain.entity.ExperimentWhitelist;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.whitelist.ExperimentWhitelistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 实验白名单管理API
 */
@RestController
@RequestMapping("/api/v1/admin/whitelist")
@RequiredArgsConstructor
@Tag(name = "Experiment Whitelist API", description = "实验白名单管理接口")
public class ExperimentWhitelistController {

    private final ExperimentWhitelistService whitelistService;

    @PostMapping("/experiments/{expId}/buckets/{bucketId}/users")
    @Operation(summary = "添加白名单用户", description = "向实验指定分桶添加白名单用户")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<ExperimentWhitelist> addUsers(
            @Parameter(description = "实验业务ID") @PathVariable String expId,
            @Parameter(description = "分桶ID") @PathVariable String bucketId,
            @Parameter(description = "用户ID列表，逗号分隔") @RequestParam String userIds) {
        ExperimentWhitelist whitelist = whitelistService.addUsers(expId, bucketId, userIds);
        return ResponseEntity.ok(whitelist);
    }

    @DeleteMapping("/experiments/{expId}/buckets/{bucketId}/users")
    @Operation(summary = "移除白名单用户", description = "从实验指定分桶移除白名单用户")
    @RequirePermission(Permission.EDIT_EXPERIMENT)
    public ResponseEntity<Void> removeUsers(
            @Parameter(description = "实验业务ID") @PathVariable String expId,
            @Parameter(description = "分桶ID") @PathVariable String bucketId,
            @Parameter(description = "用户ID列表，逗号分隔") @RequestParam String userIds) {
        whitelistService.removeUsers(expId, bucketId, userIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/experiments/{expId}")
    @Operation(summary = "获取实验白名单", description = "查询实验的所有白名单用户")
    public ResponseEntity<List<ExperimentWhitelist>> getWhitelist(
            @Parameter(description = "实验业务ID") @PathVariable String expId) {
        List<ExperimentWhitelist> whitelist = whitelistService.getWhitelist(expId);
        return ResponseEntity.ok(whitelist);
    }

    @GetMapping("/experiments/{expId}/buckets/{bucketId}")
    @Operation(summary = "获取实验指定分桶白名单", description = "查询实验指定分桶的白名单用户")
    public ResponseEntity<List<ExperimentWhitelist>> getWhitelistByBucket(
            @Parameter(description = "实验业务ID") @PathVariable String expId,
            @Parameter(description = "分桶ID") @PathVariable String bucketId) {
        List<ExperimentWhitelist> whitelist = whitelistService.getWhitelistByBucket(expId, bucketId);
        return ResponseEntity.ok(whitelist);
    }

    @GetMapping("/users/{userId}/check")
    @Operation(summary = "检查用户是否在白名单", description = "检查用户是否在某个实验的任意白名单中")
    public ResponseEntity<String> checkUserInWhitelist(
            @Parameter(description = "实验业务ID") @RequestParam String expId,
            @Parameter(description = "用户ID") @PathVariable String userId) {
        String bucketId = whitelistService.getBucketIdForWhitelistedUser(expId, userId);
        return ResponseEntity.ok(bucketId);
    }
}
