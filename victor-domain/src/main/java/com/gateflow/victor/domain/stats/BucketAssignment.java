package com.gateflow.victor.domain.stats;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分桶结果模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BucketAssignment {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 实验标识
     */
    private String experimentKey;

    /**
     * 桶号 (0-9999)
     */
    private Integer bucket;

    /**
     * 分配的版本标识
     */
    private String variant;

    /**
     * 层标识
     */
    private String layerId;
}