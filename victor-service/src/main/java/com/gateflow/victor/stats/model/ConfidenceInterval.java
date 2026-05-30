package com.gateflow.victor.stats.model;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor(staticName = "of")
public class ConfidenceInterval {
    private double lower;
    private double upper;
    private double confidenceLevel;
}