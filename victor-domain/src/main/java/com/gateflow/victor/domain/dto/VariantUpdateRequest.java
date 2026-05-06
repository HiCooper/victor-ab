package com.gateflow.victor.domain.dto;

import lombok.Data;

/**
 * 更新版本请求DTO
 */
@Data
public class VariantUpdateRequest {

    /**
     * 版本名称
     */
    private String name;

    /**
     * 桶起始位置 (0 - 9999)
     */
    private Integer bucketStart;

    /**
     * 桶结束位置 (0 - 9999)
     */
    private Integer bucketEnd;

    /**
     * 版本参数 (JSON字符串)
     */
    private String params;
}