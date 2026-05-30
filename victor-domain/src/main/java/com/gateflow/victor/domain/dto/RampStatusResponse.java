package com.gateflow.victor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 灰度状态响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RampStatusResponse {

    /**
     * 实验业务ID
     */
    private String experimentId;

    /**
     * 当前灰度阶段标签
     */
    private String currentStage;

    /**
     * 当前流量百分比
     */
    private String currentTraffic;

    /**
     * 下一阶段标签（已全量时为"已全量"）
     */
    private String nextStage;

    /**
     * 是否开启自动灰度推进
     */
    private Boolean autoRampEnabled;

    /**
     * 当前阶段的关注要点
     */
    private String focus;
}
