package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实验审批记录实体
 */
@Data
@TableName("victor_experiment_approval")
public class ExperimentApproval {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 实验业务ID
     */
    @TableField("exp_id")
    private String expId;

    /**
     * 审批人ID
     */
    @TableField("approver_id")
    private Long approverId;

    /**
     * 审批人名称
     */
    @TableField("approver_name")
    private String approverName;

    /**
     * 审批状态: pending/approved/rejected
     */
    private String status;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}