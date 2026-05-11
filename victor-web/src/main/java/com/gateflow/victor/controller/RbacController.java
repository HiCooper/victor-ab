package com.gateflow.victor.controller;

import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.service.rbac.RbacService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * RBAC 权限控制器
 */
@RestController
@RequestMapping("/api/v1/rbac")
@RequiredArgsConstructor
public class RbacController {

    private final RbacService rbacService;

    /**
     * 获取当前用户权限
     */
    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> getPermissions(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId) {
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
    public ResponseEntity<Map<String, Boolean>> checkPermission(
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long userId,
            @RequestParam String permission) {
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
    public ResponseEntity<Map<String, Object>> getRoles() {
        var roles = rbacService.getDefaultRoles();
        Map<String, Object> response = new HashMap<>();
        response.put("roles", roles);
        return ResponseEntity.ok(response);
    }
}