package com.gateflow.victor.stats.algorithm;

import org.apache.commons.math3.distribution.ChiSquaredDistribution;

/**
 * SRM (Sample Ratio Mismatch) Test
 * <p>
 * Uses Chi-Square test to detect if the actual traffic distribution
 * significantly deviates from the expected distribution.
 * Delegates distribution functions to commons-math3.
 */
public class SrmTest {

    /**
     * Perform Chi-Square test for Sample Ratio Mismatch
     *
     * @param observed Actual impression counts per bucket
     * @param expected Expected traffic ratios (should sum to 1.0)
     * @return p-value - if < 0.01, indicates significant mismatch
     */
    public static double chiSquareTest(long[] observed, double[] expected) {
        return chiSquareTestFull(observed, expected).pValue();
    }

    /**
     * Perform Chi-Square test returning full result with statistic.
     */
    public static ChiSquareResult chiSquareTestFull(long[] observed, double[] expected) {
        if (observed.length != expected.length) {
            throw new IllegalArgumentException("Arrays must have same length");
        }

        long total = 0;
        for (long count : observed) {
            total += count;
        }

        if (total == 0) {
            return new ChiSquareResult(0, 1.0, observed.length - 1);
        }

        double chiSquare = 0;
        for (int i = 0; i < observed.length; i++) {
            double expectedCount = total * expected[i];
            if (expectedCount == 0) continue;

            double diff = observed[i] - expectedCount;
            chiSquare += (diff * diff) / expectedCount;
        }

        int df = observed.length - 1;
        ChiSquaredDistribution dist = new ChiSquaredDistribution(df);
        double pValue = 1.0 - dist.cumulativeProbability(chiSquare);
        return new ChiSquareResult(chiSquare, pValue, df);
    }

    /**
     * Chi-Square test result containing both statistic and p-value.
     */
    public record ChiSquareResult(double chiSquare, double pValue, int degreesOfFreedom) {
    }
}
