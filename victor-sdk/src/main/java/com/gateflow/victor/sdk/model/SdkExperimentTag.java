package com.gateflow.victor.sdk.model;

import lombok.Data;

/**
 * 实验标签模型 - SDK内部使用
 * 
 * 用于埋点上报，记录用户参与的实验信息
 */
@Data
public class SdkExperimentTag {

    /**
     * 实验标识
     */
    private String expId;

    /**
     * 分配的版本
     */
    private String variant;

    /**
     * 所属层标识
     */
    private String layer;
}