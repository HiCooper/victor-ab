package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实验实体
 */
@Data
@TableName("victor_experiment")
public class Experiment {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务ID (用于API调用)
     */
    @TableField("exp_id")
    private String expId;

    /**
     * 实验名称
     */
    private String name;

    /**
     * 实验描述
     */
    private String description;

    /**
     * 所属层ID (引用victor_layer.id)
     */
    @TableField("layer_id")
    private Long layerId;

    /**
     * 实验状态
     */
    private String status;

    /**
     * 流量占比 (0.0 - 1.0)
     */
    @TableField("traffic_ratio")
    private BigDecimal trafficRatio;

    /**
     * 桶起始位置 (0 - 9999)
     */
    @TableField("bucket_start")
    private Integer bucketStart;

    /**
     * 桶结束位置 (0 - 9999)
     */
    @TableField("bucket_end")
    private Integer bucketEnd;

    /**
     * 定向规则 (JSON)
     */
    @TableField("targeting_rules")
    private String targetingRules;

    /**
     * 主指标
     */
    @TableField("primary_metric")
    private String primaryMetric;

    /**
     * 次指标列表 (JSON)
     */
    @TableField("secondary_metrics")
    private String secondaryMetrics;

    /**
     * 护栏指标列表 (JSON)
     */
    @TableField("guardrail_metrics")
    private String guardrailMetrics;

    /**
     * 实验开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 实验结束时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;

    /**
     * 创建人
     */
    @TableField("created_by")
    private String createdBy;

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

    /**
     * 关联的版本列表 (一对多，非数据库字段)
     */
    @TableField(exist = false)
    private java.util.List<Variant> variants;
}