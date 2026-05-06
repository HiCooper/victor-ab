package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置版本表 - 支持配置变更追踪
 */
@Data
@TableName("victor_config_version")
public class ConfigVersion {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 版本号 (如: 20260505-143000)
     */
    private String version;

    /**
     * 配置摘要 (ETag)
     */
    private String etag;

    /**
     * 完整配置JSON快照
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 变更类型 (full/incremental)
     */
    @TableField("change_type")
    private String changeType;

    /**
     * 变更的实验ID列表 (JSON)
     */
    @TableField("changed_experiments")
    private String changedExperiments;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}