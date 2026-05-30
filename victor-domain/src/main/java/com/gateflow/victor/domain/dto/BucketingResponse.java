package com.gateflow.victor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分桶响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BucketingResponse {

    /**
     * 实验标识
     */
    private String experimentKey;

    /**
     * 分配的版本标识 (null表示未命中实验)
     */
    private Integer bucket;

    /**
     * 版本参数 (JSON格式)
     */
    private String params;
}