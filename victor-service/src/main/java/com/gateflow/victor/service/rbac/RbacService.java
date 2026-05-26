package com.gateflow.victor.service.rbac;

import com.gateflow.victor.domain.entity.Permission;
import com.gateflow.victor.domain.entity.Role;
import com.gateflow.victor.domain.entity.UserRole;
import com.gateflow.victor.domain.entity.RolePermission;
import com.gateflow.victor.infra.mapper.RoleMapper;
import com.gateflow.victor.infra.mapper.UserRoleMapper;
import com.gateflow.victor.infra.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RBAC 权限服务
 */
@Service
@RequiredArgsConstructor
public class RbacService {

    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    /**
     * 根据用户ID获取用户的权限集合
     */
    public Set<Permission> getPermissionsByUserId(Long userId) {
        // 获取用户的所有角色
        List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
        if (userRoles.isEmpty()) {
            return Set.of();
        }

        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());

        // 获取所有角色的权限
        List<RolePermission> rolePermissions = rolePermissionMapper.selectByRoleIds(roleIds);

        return rolePermissions.stream()
                .map(rp -> {
                    try {
                        return Permission.valueOf(rp.getPermission());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(p -> p != null)
                .collect(Collectors.toSet());
    }

    /**
     * 检查用户是否拥有指定权限
     */
    public boolean hasPermission(Long userId, Permission permission) {
        return getPermissionsByUserId(userId).contains(permission);
    }

    /**
     * 检查用户是否拥有指定角色
     */
    public boolean hasRole(Long userId, String roleName) {
        List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());

        if (roleIds.isEmpty()) {
            return false;
        }

        List<Role> roles = roleMapper.selectByIds(roleIds);
        return roles.stream()
                .anyMatch(r -> r.getName().equals(roleName));
    }

    /**
     * 获取用户的角色名称列表
     */
    public List<String> getRoleNamesByUserId(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectByUserId(userId);
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
        return roleMapper.selectByIds(roleIds).stream()
                .map(Role::getName)
                .collect(Collectors.toList());
    }

    /**
     * 获取默认角色列表（用于初始化）
     */
    public List<Role> getDefaultRoles() {
        return roleMapper.selectAll();
    }
}