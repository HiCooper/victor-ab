package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.SampleStatistics;
import com.gateflow.victor.stats.model.SequentialTestResult;
import com.gateflow.victor.stats.model.SequentialStatus;
import org.springframework.stereotype.Component;

@Component
public class mSPRT {
    private static final double ALPHA = 0.05;
    private static final double TAU = 0.5;

    public SequentialTestResult execute(
        SampleStatistics control, SampleStatistics treatment, int cumulativeObs
    ) {
        double delta = treatment.getMean() - control.getMean();
        double sigma2 = treatment.getVariance() + control.getVariance();
        double n = treatment.getN();
        
        double denominator = sigma2 + n * TAU * TAU;
        double lambda = Math.sqrt(sigma2 / denominator) * 
            Math.exp(n * delta * delta / (2 * denominator));
        
        double upperBound = 1 / ALPHA;
        double lowerBound = ALPHA;
        
        SequentialStatus status;
        String message;
        if (lambda > upperBound) {
            status = SequentialStatus.STOP_NEGATIVE;
            message = "护栏指标显著恶化，建议停止实验";
        } else if (lambda < lowerBound) {
            status = SequentialStatus.STOP_SAFE;
            message = "护栏指标安全";
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