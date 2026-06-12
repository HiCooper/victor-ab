package com.gateflow.victor.common.util;

import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;

/**
 * Shared statistical utilities to eliminate duplicated normalCDF/Gamma wrappers
 * across service classes.
 */
public final class StatsUtils {

    private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution(0, 1);

    private StatsUtils() {}

    /** Standard normal CDF — Φ(x). */
    public static double normalCDF(double x) {
        return STANDARD_NORMAL.cumulativeProbability(x);
    }

    /** Two-tailed p-value from z-score. */
    public static double twoTailedPValue(double z) {
        return 2 * (1 - normalCDF(Math.abs(z)));
    }

    /**
     * Sample from Gamma(shape, scale) distribution.
     * Used for Beta-distribution sampling via
     * Beta(α,β) = Gamma(α,1) / (Gamma(α,1) + Gamma(β,1)).
     */
    public static double gammaSample(double shape, double scale) {
        return new GammaDistribution(shape, scale).sample();
    }
}
