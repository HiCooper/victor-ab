package com.gateflow.victor.domain.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 人群拆分分析响应
 */
@Data
public class SubgroupAnalysisResponse {

    private Long experimentId;
    private String experimentKey;
    private String experimentName;
    private String dimension;
    private LocalDate startDate;
    private LocalDate endDate;
    private String primaryMetric;
    private List<SubgroupResult> subgroups;
    private String overallLift;
    private String overallPValue;

    @Data
    public static class SubgroupResult {
        private String groupValue;
        private int totalUsers;
        private int controlUsers;
        private int treatmentUsers;
        private double controlConversionRate;
        private double treatmentConversionRate;
        private String lift;
        private double pValue;
        private boolean significant;
        private String confidenceInterval;
        private String interpretation;
    }
}
