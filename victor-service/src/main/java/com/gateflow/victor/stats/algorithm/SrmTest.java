package com.gateflow.victor.stats.algorithm;

/**
 * SRM (Sample Ratio Mismatch) Test
 *
 * Uses Chi-Square test to detect if the actual traffic distribution
 * significantly deviates from the expected distribution.
 */
public class SrmTest {

    /**
     * Chi-Square test result containing both statistic and p-value.
     */
    public record ChiSquareResult(double chiSquare, double pValue, int degreesOfFreedom) {}

    /**
     * Perform Chi-Square test for Sample Ratio Mismatch
     *
     * @param observed Actual impression counts per variant
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
        double pValue = 1.0 - chiSquareCDF(chiSquare, df);
        return new ChiSquareResult(chiSquare, pValue, df);
    }

    private static double chiSquareCDF(double x, int k) {
        if (x < 0) return 0;
        if (x == 0) return 0;
        return regularizedGammaP(k / 2.0, x / 2.0);
    }

    private static double regularizedGammaP(double a, double x) {
        if (x < 0 || a <= 0) {
            throw new IllegalArgumentException("Invalid parameters");
        }
        
        if (x < a + 1) {
            return gammaSeries(a, x);
        } else {
            return 1.0 - gammaContinuedFraction(a, x);
        }
    }

    private static double gammaSeries(double a, double x) {
        double sum = 1.0 / a;
        double term = sum;
        
        for (int n = 1; n <= 100; n++) {
            term *= x / (a + n);
            sum += term;
            if (Math.abs(term) < 1e-10 * Math.abs(sum)) {
                break;
            }
        }
        
        return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
    }

    private static double gammaContinuedFraction(double a, double x) {
        double b = x + 1 - a;
        double c = 1.0 / 1e-30;
        double d = 1.0 / b;
        double h = d;
        
        for (int i = 1; i <= 100; i++) {
            double an = -i * (i - a);
            b += 2;
            d = an * d + b;
            if (Math.abs(d) < 1e-30) d = 1e-30;
            c = b + an / c;
            if (Math.abs(c) < 1e-30) c = 1e-30;
            d = 1.0 / d;
            double delta = d * c;
            h *= delta;
            if (Math.abs(delta - 1.0) < 1e-10) {
                break;
            }
        }
        
        return Math.exp(-x + a * Math.log(x) - logGamma(a)) * h;
    }

    private static double logGamma(double x) {
        double[] g = {
            76.18009172947146,
            -86.50532032941677,
            24.01409824083091,
            -1.231739572450155,
            0.1208650973866179e-2,
            -0.5395239384953e-5
        };
        
        double y = x;
        double tmp = x + 5.5;
        tmp -= (x + 0.5) * Math.log(tmp);
        
        double ser = 1.000000000190015;
        for (int j = 0; j < 6; j++) {
            ser += g[j] / ++y;
        }
        
        return -tmp + Math.log(2.5066282746310005 * ser / x);
    }
}
