package com.gateflow.victor.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 层实体 - 正交分层
 */
@Data
@TableName("victor_layer")
public class Layer {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 业务ID (用于API调用)
     */
    @TableField("layer_id")
    private String layerId;

    /**
     * 所属域ID (引用victor_domain.id)
     */
    @TableField("domain_id")
    private Long domainId;

    /**
     * 层名称
     */
    private String name;

    /**
     * 盐值 (用于哈希计算，实现层间正交)
     */
    private String salt;

    /**
     * 排序顺序
     */
    @TableField("sort_order")
    private Integer sortOrder;

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