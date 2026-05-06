package com.gateflow.victor.stats.model;

import lombok.Data;
import lombok.Builder;
import java.util.List;

@Data
@Builder
public class SampleStatistics {
    private long n;
    private double mean;
    private double variance;
    private double sum;
    
    public static SampleStatistics fromProportion(long successes, long trials) {
        double p = (double) successes / trials;
        return SampleStatistics.builder()
            .n(trials)
            .mean(p)
            .variance(p * (1 - p))
            .sum(successes)
            .build();
    }
    
    public static SampleStatistics fromValues(List<Double> values) {
        long n = values.size();
        double sum = values.stream().mapToDouble(d -> d).sum();
        double mean = sum / n;
        double variance = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum() / (n - 1);
        return SampleStatistics.builder()
            .n(n)
            .mean(mean)
            .variance(variance)
            .sum(sum)
            .build();
    }
}