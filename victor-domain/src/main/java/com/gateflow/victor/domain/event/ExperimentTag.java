package com.gateflow.victor.domain.event;

import lombok.Data;

import java.util.List;
import java.util.Map;

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
    private String variant;

    /**
     * 层标识
     */
    private String layer;
}