package com.gateflow.victor.stats.algorithm;

import com.gateflow.victor.stats.model.SampleStatistics;
import com.gateflow.victor.stats.model.SequentialTestResult;
import com.gateflow.victor.stats.model.SequentialStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class mSPRTTest {
    @Test
    void shouldContinueWhenNoDifference() {
        mSPRT msprt = new mSPRT();
        SampleStatistics control = SampleStatistics.builder().n(1000).mean(50).variance(100).build();
        SampleStatistics treatment = SampleStatistics.builder().n(1000).mean(50).variance(100).build();
        
        SequentialTestResult result = msprt.execute(control, treatment, 1);
        assertEquals(SequentialStatus.CONTINUE, result.getStatus());
    }
}