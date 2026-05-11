package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户角色实体
 */
@Data
@TableName("rbac_role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色名称: ADMIN, OPERATOR, VIEWER
     */
    private String name;

    /**
     * 角色描述
     */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}