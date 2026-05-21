package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.TestResult;
import com.gateflow.victor.stats.model.LiftEstimate;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BHCorrectionTest {
    @Test
    void shouldCorrectMultiplePValues() {
        BHCorrection bh = new BHCorrection();
        List<TestResult> results = Arrays.asList(
            TestResult.builder().testName("m1").pValue(0.01).lift(LiftEstimate.of(0.1,0,0)).build(),
            TestResult.builder().testName("m2").pValue(0.04).lift(LiftEstimate.of(0.05,0,0)).build(),
            TestResult.builder().testName("m3").pValue(0.06).lift(LiftEstimate.of(0.02,0,0)).build()
        );
        
        List<TestResult> corrected = bh.correct(results);
        // BH校正后：阈值依次为0.017,0.033,0.05，只有p=0.01<=0.017显著
        assertTrue(corrected.get(0).isSignificant());
        assertFalse(corrected.get(1).isSignificant());
        assertFalse(corrected.get(2).isSignificant());
    }
}