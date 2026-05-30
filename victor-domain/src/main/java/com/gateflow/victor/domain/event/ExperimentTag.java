package com.gateflow.victor.domain.event;

import lombok.Data;

/**
 * 实验标签 - 用于埋点
 */
@Data
public class ExperimentTag {

    /**
     * 实验标识
     */
    private String expId;

    /**
     * 版本标识
     */
    private String bucket;

    /**
     * 层标识
     */
    private String layer;
}