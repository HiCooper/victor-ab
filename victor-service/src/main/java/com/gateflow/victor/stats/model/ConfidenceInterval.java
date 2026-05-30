package com.gateflow.victor.stats.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class ConfidenceInterval {
    private double lower;
    private double upper;
    private double confidenceLevel;
}