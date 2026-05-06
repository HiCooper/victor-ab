package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 域实体 - 流量分域
 */
@Data
@TableName("victor_domain")
public class Domain {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务ID (用于API调用)
     */
    @TableField("domain_id")
    private String domainId;

    /**
     * 名称
     */
    private String name;

    /**
     * 流量占比 (0.0 - 1.0)
     */
    @TableField("traffic_ratio")
    private BigDecimal trafficRatio;

    /**
     * 是否独占域 (独占域内实验互斥)
     */
    @TableField("is_exclusive")
    private Boolean isExclusive;

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