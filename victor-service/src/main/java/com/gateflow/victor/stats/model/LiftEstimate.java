package com.gateflow.victor.stats.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class LiftEstimate {
    private double value;
    private double confidenceIntervalLower;
    private double confidenceIntervalUpper;
}