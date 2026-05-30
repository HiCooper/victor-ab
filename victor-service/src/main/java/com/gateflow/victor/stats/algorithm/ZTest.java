package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.ConfidenceInterval;
import com.gateflow.victor.stats.model.LiftEstimate;
import com.gateflow.victor.stats.model.SampleStatistics;
import com.gateflow.victor.stats.model.TestResult;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.springframework.stereotype.Component;

@Component
public class ZTest {
    private static final double ALPHA = 0.05;
    private static final double Z_CRITICAL = 1.96;

    /**
     * Execute ZTest using pre-computed SampleStatistics (e.g., CUPED-adjusted).
     * Uses unpooled standard error based on per-group variances, which is
     * appropriate for CUPED-adjusted statistics where variance is reduced.
     */
    public TestResult executeWithStats(SampleStatistics control, SampleStatistics treatment) {
        if (control.getN() == 0 || treatment.getN() == 0) {
            return TestResult.builder()
                    .testName("z_test_cuped")
                    .statistic(Double.NaN)
                    .pValue(1.0)
                    .significant(false)
                    .lift(LiftEstimate.of(0, 0, 0))
                    .confidenceInterval(ConfidenceInterval.of(0, 0, 0.95))
                    .message("Insufficient data: zero sample size")
                    .build();
        }

        double diff = treatment.getMean() - control.getMean();
        double se = Math.sqrt(
                control.getVariance() / control.getN() + treatment.getVariance() / treatment.getN()
        );

        if (se == 0) {
            return TestResult.builder()
                    .testName("z_test_cuped")
                    .statistic(0)
                    .pValue(1.0)
                    .significant(false)
                    .lift(LiftEstimate.of(0, 0, 0))
                    .confidenceInterval(ConfidenceInterval.of(0, 0, 0.95))
                    .message("No variance in data")
                    .build();
        }

        double z = diff / se;

        NormalDistribution normal = new NormalDistribution();
        double pValue = 2 * (1 - normal.cumulativeProbability(Math.abs(z)));

        double ciLower = diff - Z_CRITICAL * se;
        double ciUpper = diff + Z_CRITICAL * se;

        double lift = control.getMean() != 0 ? diff / control.getMean() : 0;
        double liftCiLower = control.getMean() != 0 ? ciLower / control.getMean() : 0;
        double liftCiUpper = control.getMean() != 0 ? ciUpper / control.getMean() : 0;

        return TestResult.builder()
                .testName("z_test_cuped")
                .statistic(z)
                .pValue(pValue)
                .significant(pValue < ALPHA)
                .lift(LiftEstimate.of(lift, liftCiLower, liftCiUpper))
                .confidenceInterval(ConfidenceInterval.of(ciLower, ciUpper, 0.95))
                .build();
    }

    public TestResult executeProportion(
            long controlSuccess, long controlTotal,
            long treatmentSuccess, long treatmentTotal
    ) {
        if (controlTotal == 0 || treatmentTotal == 0) {
            return TestResult.builder()
                    .testName("z_test")
                    .statistic(Double.NaN)
                    .pValue(1.0)
                    .significant(false)
                    .lift(LiftEstimate.of(0, 0, 0))
                    .confidenceInterval(ConfidenceInterval.of(0, 0, 0.95))
                    .message("Insufficient data: control or treatment has zero users")
                    .build();
        }

        double pControl = (double) controlSuccess / controlTotal;
        double pTreatment = (double) treatmentSuccess / treatmentTotal;

        double pPooled = (double) (controlSuccess + treatmentSuccess) /
                (controlTotal + treatmentTotal);

        double se = Math.sqrt(
                pPooled * (1 - pPooled) * (1.0 / controlTotal + 1.0 / treatmentTotal)
        );

        double z = (pTreatment - pControl) / se;

        NormalDistribution normal = new NormalDistribution();
        double pValue = 2 * (1 - normal.cumulativeProbability(Math.abs(z)));

        double seUnpooled = Math.sqrt(
                pControl * (1 - pControl) / controlTotal +
                        pTreatment * (1 - pTreatment) / treatmentTotal
        );
        double diff = pTreatment - pControl;
        double ciLower = diff - Z_CRITICAL * seUnpooled;
        double ciUpper = diff + Z_CRITICAL * seUnpooled;

        double lift = pControl != 0 ? diff / pControl : 0;
        double liftCiLower = pControl != 0 ? ciLower / pControl : 0;
        double liftCiUpper = pControl != 0 ? ciUpper / pControl : 0;

        return TestResult.builder()
                .testName("z_test")
                .statistic(z)
                .pValue(pValue)
                .significant(pValue < ALPHA)
                .lift(LiftEstimate.of(lift, liftCiLower, liftCiUpper))
                .confidenceInterval(ConfidenceInterval.of(ciLower, ciUpper, 0.95))
                .build();
    }
}