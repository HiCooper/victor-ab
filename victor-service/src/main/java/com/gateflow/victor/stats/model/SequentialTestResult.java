package com.gateflow.victor.stats.model;

import lombok.Builder;
import lombok.Data;

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