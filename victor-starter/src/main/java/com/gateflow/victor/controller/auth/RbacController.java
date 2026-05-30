package com.gateflow.victor.controller.auth;

import com.gateflow.victor.domain.dto.RbacPermissionsResponse;
import com.gateflow.victor.domain.dto.RbacRolesResponse;
import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.domain.entity.Role;
import com.gateflow.victor.service.rbac.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
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
        throw new AccessDeniedException("Authentication required");
    }

    /**
     * 获取当前用户权限
     */
    @GetMapping("/permissions")
    @Operation(summary = "获取当前用户权限", description = "返回当前用户的所有权限及角色")
    public ResponseEntity<RbacPermissionsResponse> getPermissions() {
        Long userId = getCurrentUserId();
        Set<Permission> permissions = rbacService.getPermissionsByUserId(userId);
        RbacPermissionsResponse response = new RbacPermissionsResponse();
        response.setUserId(userId);
        response.setPermissions(permissions.stream().map(Permission::name).toList());
        response.setAdmin(rbacService.hasRole(userId, "ADMIN"));
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
    public ResponseEntity<RbacRolesResponse> getRoles() {
        List<Role> roles = rbacService.getDefaultRoles();
        List<Map<String, String>> roleMaps = roles.stream()
                .map(r -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("name", r.getName());
                    m.put("description", r.getDescription());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(new RbacRolesResponse(roleMaps));
    }
}