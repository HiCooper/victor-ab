package com.gateflow.victor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConfidenceTrendResponse {
    private List<DataPoint> data;

    @Data
    @Builder
    public static class DataPoint {
        private String date;
        private double confidence;
    }
}
