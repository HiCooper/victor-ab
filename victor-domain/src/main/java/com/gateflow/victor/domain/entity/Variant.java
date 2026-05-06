package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本实体
 */
@Data
@TableName("victor_variant")
public class Variant {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属实验ID (引用victor_experiment.id)
     */
    @TableField("exp_id")
    private Long expId;

    /**
     * 版本号 (时间戳格式: 20260506143000)
     */
    @TableField("version")
    private String version;

    /**
     * 版本标识 (如: control, treatment_a)
     */
    @TableField("variant_key")
    private String variantKey;

    /**
     * 版本名称
     */
    private String name;

    /**
     * 桶起始位置
     */
    @TableField("bucket_start")
    private Integer bucketStart;

    /**
     * 桶结束位置
     */
    @TableField("bucket_end")
    private Integer bucketEnd;

    /**
     * 版本参数 (VARCHAR(64)，支持任意字符串格式)
     */
    private String params;

    /**
     * 是否为当前活跃版本
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}