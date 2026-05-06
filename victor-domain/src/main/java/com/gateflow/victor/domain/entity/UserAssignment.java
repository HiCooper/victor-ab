package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户分桶记录 - 用于审计和SRM检验
 */
@Data
@TableName("victor_user_assignment")
public class UserAssignment {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 实验ID (引用victor_experiment.id)
     */
    @TableField("exp_id")
    private Long expId;

    /**
     * 分配的版本标识
     */
    @TableField("variant_key")
    private String variantKey;

    /**
     * 分桶位置 (0-9999)
     */
    private Integer bucket;

    /**
     * 分配时间
     */
    @TableField("assigned_at")
    private LocalDateTime assignedAt;
}