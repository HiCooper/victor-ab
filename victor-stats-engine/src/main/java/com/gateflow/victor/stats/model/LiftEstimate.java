package com.gateflow.victor.stats.model;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor(staticName = "of")
public class LiftEstimate {
    private double value;
    private double confidenceIntervalLower;
    private double confidenceIntervalUpper;
}