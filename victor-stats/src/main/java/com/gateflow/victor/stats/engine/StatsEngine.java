package com.gateflow.victor.stats.engine;

import com.gateflow.victor.stats.algorithm.*;
import com.gateflow.victor.stats.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsEngine {
    private final ZTest zTest;
    private final CUPED cuped;
    private final BHCorrection bhCorrection;
    private final mSPRT msprt;

    public double runSRM(long[] observed, double[] expected) {
        return SrmTest.chiSquareTest(observed, expected);
    }

    public TestResult runZTest(long cSuccess, long cTotal, long tSuccess, long tTotal) {
        return zTest.executeProportion(cSuccess, cTotal, tSuccess, tTotal);
    }

    public SampleStatistics runCUPED(List<Double> y, List<Double> x, double meanX) {
        return cuped.adjust(y, x, meanX);
    }

    public List<TestResult> runBHCorrection(List<TestResult> results) {
        return bhCorrection.correct(results);
    }

    public SequentialTestResult runmSPRT(SampleStatistics c, SampleStatistics t, int obs) {
        return msprt.execute(c, t, obs);
    }
}