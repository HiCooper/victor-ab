package com.gateflow.victor.stats.model;

import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class SequentialTestResult {
    private String testName;
    private double lambda;
    private double upperBound;
    private double lowerBound;
    private SequentialStatus status;
    private int cumulativeObservations;
    private String message;
}