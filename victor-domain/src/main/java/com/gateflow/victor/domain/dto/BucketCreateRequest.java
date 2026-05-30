package com.gateflow.victor.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建版本请求DTO
 */
@Data
public class BucketCreateRequest {

    /**
     * 所属实验业务ID (victor_experiment.exp_id)
     */
    @NotBlank(message = "expId不能为空")
    private String expId;

    /**
     * 版本名称
     */
    private String name;

    /**
     * 桶起始位置 (0 - 9999)
     */
    @NotNull(message = "bucketStart不能为空")
    private Integer bucketStart;

    /**
     * 桶结束位置 (0 - 9999)
     */
    @NotNull(message = "bucketEnd不能为空")
    private Integer bucketEnd;

    /**
     * 版本参数 (JSON字符串)
     */
    private String params;
}