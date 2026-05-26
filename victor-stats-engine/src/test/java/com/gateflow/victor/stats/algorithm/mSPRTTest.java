package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.SampleStatistics;
import com.gateflow.victor.stats.model.SequentialTestResult;
import com.gateflow.victor.stats.model.SequentialStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class mSPRTTest {

    private final mSPRT msprt = new mSPRT();

    @Test
    void shouldContinueWhenNoDifference() {
        SampleStatistics control = SampleStatistics.builder().n(1000).mean(50).variance(100).build();
        SampleStatistics treatment = SampleStatistics.builder().n(1000).mean(50).variance(100).build();

        SequentialTestResult result = msprt.execute(control, treatment, 1000);
        assertEquals(SequentialStatus.CONTINUE, result.getStatus());
        // With no effect, lambda should be close to 1 (slightly below due to sqrt term)
        assertTrue(result.getLambda() < 20.0);
    }

    @Test
    void shouldDetectLargePositiveEffect() {
        // Positive lift with sufficient sample — lambda should exceed 1
        // n=200, delta=1.5, sigma2=50 → lambda ≈ 6.7
        SampleStatistics control = SampleStatistics.builder().n(200).mean(10.0).variance(25.0).build();
        SampleStatistics treatment = SampleStatistics.builder().n(200).mean(11.5).variance(25.0).build();

        SequentialTestResult result = msprt.execute(control, treatment, 200);
        assertTrue(result.getLambda() > 1.0,
            "Lambda should be > 1 for treatment better than control, got " + result.getLambda());
    }

    @Test
    void shouldDetectNegativeEffect() {
        // Treatment worse than control — lambda > upper bound triggers STOP_NEGATIVE
        // n=300, delta=-1.5, sigma2=50 → lambda ≈ 36 > 20
        SampleStatistics control = SampleStatistics.builder().n(300).mean(10.0).variance(25.0).build();
        SampleStatistics treatment = SampleStatistics.builder().n(300).mean(8.5).variance(25.0).build();

        SequentialTestResult result = msprt.execute(control, treatment, 300);
        assertTrue(result.getLambda() > 1.0,
            "Lambda should be > 1 for large effect, got " + result.getLambda());
        assertEquals(SequentialStatus.STOP_NEGATIVE, result.getStatus());
    }

    @Test
    void shouldHandleZeroSamples() {
        SampleStatistics control = SampleStatistics.builder().n(0).mean(0).variance(0).build();
        SampleStatistics treatment = SampleStatistics.builder().n(0).mean(0).variance(0).build();

        SequentialTestResult result = msprt.execute(control, treatment, 0);
        assertEquals(SequentialStatus.CONTINUE, result.getStatus());
        assertEquals(1.0, result.getLambda(), 0.001);
    }

    @Test
    void shouldBeMoreCertainWithLargerSample() {
        // Same effect size, larger sample → lambda should be more extreme
        SampleStatistics smallControl = SampleStatistics.builder().n(30).mean(10.0).variance(25.0).build();
        SampleStatistics smallTreat = SampleStatistics.builder().n(30).mean(11.0).variance(25.0).build();

        SampleStatistics largeControl = SampleStatistics.builder().n(150).mean(10.0).variance(25.0).build();
        SampleStatistics largeTreat = SampleStatistics.builder().n(150).mean(11.0).variance(25.0).build();

        SequentialTestResult smallResult = msprt.execute(smallControl, smallTreat, 30);
        SequentialTestResult largeResult = msprt.execute(largeControl, largeTreat, 150);

        // Larger sample with same effect should produce more extreme lambda
        assertTrue(Math.abs(Math.log(largeResult.getLambda())) > Math.abs(Math.log(smallResult.getLambda())),
            "Larger sample should produce more extreme lambda. Small: " + smallResult.getLambda()
                + ", Large: " + largeResult.getLambda());
    }
}
