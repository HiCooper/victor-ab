package com.gateflow.victor.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建层请求DTO
 */
@Data
public class LayerCreateRequest {

    /**
     * 业务ID (用于API调用)
     */
    @NotBlank(message = "layerId不能为空")
    private String layerId;

    /**
     * 所属域ID
     */
    private Long domainId;

    /**
     * 层名称
     */
    @NotBlank(message = "name不能为空")
    private String name;

    /**
     * 盐值 (可选，不提供则自动生成)
     */
    private String salt;

    /**
     * 排序顺序
     */
    private Integer sortOrder;
}