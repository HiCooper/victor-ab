package com.gateflow.victor.domain.dto;

import lombok.Data;

/**
 * 更新层请求DTO
 */
@Data
public class LayerUpdateRequest {

    /**
     * 层名称
     */
    private String name;

    /**
     * 排序顺序
     */
    private Integer sortOrder;
}