package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.TestResult;
import com.gateflow.victor.stats.model.LiftEstimate;
import com.gateflow.victor.stats.model.ConfidenceInterval;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.springframework.stereotype.Component;

@Component
public class ZTest {
    private static final double ALPHA = 0.05;
    private static final double Z_CRITICAL = 1.96;

    public TestResult executeProportion(
        long controlSuccess, long controlTotal,
        long treatmentSuccess, long treatmentTotal
    ) {
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