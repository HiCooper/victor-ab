package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色权限关联实体
 */
@Data
@TableName("rbac_role_permission")
public class RolePermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 权限名称: 对应 Permission 枚举
     */
    private String permission;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}