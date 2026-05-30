package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.SampleStatistics;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CUPED {

    public SampleStatistics adjust(
            List<Double> experimentValues,
            List<Double> preExperimentValues,
            double overallMeanX
    ) {
        int n = experimentValues.size();

        double meanY = experimentValues.stream().mapToDouble(d -> d).average().orElse(0);
        double varY = calculateVariance(experimentValues, meanY);

        double meanX = preExperimentValues.stream().mapToDouble(d -> d).average().orElse(0);
        double varX = calculateVariance(preExperimentValues, meanX);

        double covXY = calculateCovariance(experimentValues, preExperimentValues, meanY, meanX);
        double theta = covXY / varX;

        List<Double> cupedValues = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double yCuped = experimentValues.get(i) - theta * (preExperimentValues.get(i) - overallMeanX);
            cupedValues.add(yCuped);
        }

        double meanCuped = cupedValues.stream().mapToDouble(d -> d).average().orElse(0);
        double varCuped = calculateVariance(cupedValues, meanCuped);

        return SampleStatistics.builder()
                .n(n)
                .mean(meanCuped)
                .variance(varCuped)
                .build();
    }

    private double calculateVariance(List<Double> values, double mean) {
        return values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / (values.size() - 1);
    }

    private double calculateCovariance(List<Double> y, List<Double> x, double meanY, double meanX) {
        double sum = 0;
        for (int i = 0; i < y.size(); i++) {
            sum += (y.get(i) - meanY) * (x.get(i) - meanX);
        }
        return sum / (y.size() - 1);
    }
}