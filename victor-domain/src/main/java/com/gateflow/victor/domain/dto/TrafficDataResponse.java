package com.gateflow.victor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 流量数据DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficDataResponse {

    private List<DataPoint> data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataPoint {
        private String date;
        private long control;
        private long treatment;
    }
}
