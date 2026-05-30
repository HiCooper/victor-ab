package com.gateflow.victor.domain.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分桶统计数据DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BucketStatisticsResponse {

    private List<BucketStat> buckets;
    private boolean srmPassed;
    private String srmMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BucketStat {
        private String bucket;
        private long dailyEntries;
        private long dailySamples;
        private double chiSquarePValue;
        private long cumulativeSamples;
        private double cumulativeChiSquarePValue;
    }
}
