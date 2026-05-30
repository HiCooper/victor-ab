package com.gateflow.victor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AA测试结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AATestResponse {

    private List<AATestResult> results;
    private boolean aaTestPassed;
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AATestResult {
        private String metric;
        private double controlMean;
        private double bucketMean;
        private double pValue;
        private boolean significant;
    }
}
