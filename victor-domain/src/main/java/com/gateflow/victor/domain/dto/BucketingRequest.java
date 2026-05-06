package com.gateflow.victor.domain.dto;

import lombok.Data;

/**
 * 分桶请求DTO
 */
@Data
public class BucketingRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 实验标识 (可选，不指定则返回所有实验结果)
     */
    private String experimentKey;

    /**
     * 平台
     */
    private String platform;
}