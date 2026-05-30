package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.SampleStatistics;
import com.gateflow.victor.stats.model.SequentialStatus;
import com.gateflow.victor.stats.model.SequentialTestResult;
import org.springframework.stereotype.Component;

/**
 * mSPRT — Mixed Sequential Probability Ratio Test (Johari et al. 2017).
 * <p>
 * Tests H0: θ=0 vs H1: θ~N(0,τ²) with a mixture prior on the alternative.
 * The test statistic Λ_n is compared to boundaries 1/α (reject H0) and α (accept H0).
 * <p>
 * Lambda formula:
 * Λ_n = √(σ² / (σ² + n·τ²)) · exp( n · δ̂² · τ² / (2 · σ² · (σ² + n·τ²)) )
 * <p>
 * where:
 * δ̂   = estimated treatment effect (mean_t - mean_c)
 * σ²  = per-observation variance of the difference (Var(X-Y) = Var(X) + Var(Y))
 * n   = cumulative number of observations per group (effective sample size)
 * τ   = mixing parameter controlling sensitivity
 * <p>
 * Callers pass cumulative aggregate statistics at each check point.
 * The sequential property is preserved because cumulative aggregates grow monotonically.
 */
@Component
public class mSPRT {
    private static final double ALPHA = 0.05;
    private static final double TAU = 0.5;

    /**
     * Execute one sequential check.
     *
     * @param control       cumulative control group statistics
     * @param treatment     cumulative treatment group statistics
     * @param cumulativeObs cumulative observation count (logged for traceability)
     */
    public SequentialTestResult execute(
            SampleStatistics control, SampleStatistics treatment, int cumulativeObs
    ) {
        double delta = treatment.getMean() - control.getMean();
        double sigma2 = treatment.getVariance() + control.getVariance();
        double n = treatment.getN();

        if (n == 0 || sigma2 == 0) {
            return SequentialTestResult.builder()
                    .testName("mSPRT")
                    .lambda(1.0)
                    .upperBound(1 / ALPHA)
                    .lowerBound(ALPHA)
                    .status(SequentialStatus.CONTINUE)
                    .cumulativeObservations(cumulativeObs)
                    .message("数据不足，继续监测")
                    .build();
        }

        double denominator = sigma2 + n * TAU * TAU;
        double lambda = Math.sqrt(sigma2 / denominator)
                * Math.exp(n * n * delta * delta * TAU * TAU / (2 * sigma2 * denominator));

        double upperBound = 1 / ALPHA;  // 20.0
        double lowerBound = ALPHA;       // 0.05

        SequentialStatus status;
        String message;

        if (lambda > upperBound) {
            // Reject H0 — significant effect detected.
            // Direction distinguishes positive vs negative guardrail impact.
            if (delta < 0) {
                status = SequentialStatus.STOP_NEGATIVE;
                message = "护栏指标显著恶化（delta=" + String.format("%.4f", delta)
                        + "），建议停止实验";
            } else {
                status = SequentialStatus.STOP_SAFE;
                message = "护栏指标显著改善（delta=" + String.format("%.4f", delta)
                        + "）";
            }
        } else if (lambda < lowerBound) {
            status = SequentialStatus.STOP_SAFE;
            message = "护栏指标无显著差异，安全";
        } else {
            status = SequentialStatus.CONTINUE;
            message = "继续监测";
        }

        return SequentialTestResult.builder()
                .testName("mSPRT")
                .lambda(lambda)
                .upperBound(upperBound)
                .lowerBound(lowerBound)
                .status(status)
                .cumulativeObservations(cumulativeObs)
                .message(message)
                .build();
    }
}
