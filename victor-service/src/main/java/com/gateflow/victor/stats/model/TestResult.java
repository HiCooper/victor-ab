package com.gateflow.victor.stats.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestResult {
    private String testName;
    private double statistic;
    private double pValue;
    private double adjustedPValue;
    private double degreesOfFreedom;
    private boolean significant;
    private boolean passed;
    private LiftEstimate lift;
    private ConfidenceInterval confidenceInterval;
    private String message;
}