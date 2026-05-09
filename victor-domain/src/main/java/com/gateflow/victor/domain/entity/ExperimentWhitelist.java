package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实验白名单 - 用于手动指定用户参与特定实验的特定分桶
 */
@Data
@TableName("victor_experiment_whitelist")
public class ExperimentWhitelist {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 实验业务ID (victor_experiment.exp_id)
     */
    @TableField("exp_id")
    private String expId;

    /**
     * 分桶ID (victor_variant.bucket_id)
     */
    @TableField("bucket_id")
    private String bucketId;

    /**
     * 白名单用户ID列表（逗号分隔）
     */
    @TableField("user_ids")
    private String userIds;

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
