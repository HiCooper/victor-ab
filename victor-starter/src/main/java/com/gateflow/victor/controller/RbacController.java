package com.gateflow.victor.controller;

import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.rbac.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RBAC 权限控制器
 */
@RestController
@RequestMapping("/api/v1/rbac")
@RequiredArgsConstructor
@Tag(name = "RBAC API", description = "角色权限管理接口")
public class RbacController {

    private final RbacService rbacService;

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            return (Long) auth.getPrincipal();
        }
        return 1L; // fallback for dev
    }

    /**
     * 获取当前用户权限
     */
    @GetMapping("/permissions")
    @Operation(summary = "获取当前用户权限", description = "返回当前用户的所有权限及角色")
    public ResponseEntity<Map<String, Object>> getPermissions() {
        Long userId = getCurrentUserId();
        Set<Permission> permissions = rbacService.getPermissionsByUserId(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("permissions", permissions.stream().map(Permission::name).toList());
        response.put("isAdmin", rbacService.hasRole(userId, "ADMIN"));
        return ResponseEntity.ok(response);
    }

    /**
     * 检查权限
     */
    @GetMapping("/check")
    @Operation(summary = "检查权限", description = "检查用户是否拥有指定权限")
    public ResponseEntity<Map<String, Boolean>> checkPermission(
            @RequestParam String permission) {
        Long userId = getCurrentUserId();
        try {
            Permission perm = Permission.valueOf(permission);
            boolean hasPermission = rbacService.hasPermission(userId, perm);
            Map<String, Boolean> response = new HashMap<>();
            response.put("hasPermission", hasPermission);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取角色列表
     */
    @GetMapping("/roles")
    @Operation(summary = "获取角色列表", description = "返回系统所有预定义角色及描述")
    public ResponseEntity<Map<String, Object>> getRoles() {
        var roles = rbacService.getDefaultRoles();
        Map<String, Object> response = new HashMap<>();
        response.put("roles", roles);
        return ResponseEntity.ok(response);
    }
}