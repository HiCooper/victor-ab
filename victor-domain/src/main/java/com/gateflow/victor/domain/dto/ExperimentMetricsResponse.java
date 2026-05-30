package com.gateflow.victor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实验指标结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentMetricsResponse {

    private MetricResult primary;
    private List<MetricResult> secondary;
    private List<MetricResult> guardrail;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricResult {
        private String id;
        private String name;
        private double value;
        private double lift;
        private double pValue;
        private boolean significant;
        private String direction; // positive, negative, neutral
        private double[] confidenceInterval; // [lower, upper]
    }
}
